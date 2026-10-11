package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Listing readings end to end: signed cursor, filters, permissions and aqueduct isolation. */
class ReadingListIT extends SessionIT {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  private void record(Session session, UUID tank, String value, Instant observedAt)
      throws Exception {
    mvc.perform(
            post("/api/v1/readings")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":\""
                        + UUID.randomUUID()
                        + "\",\"tank_id\":\""
                        + tank
                        + "\",\"gauge_value\":"
                        + value
                        + ",\"water_appearance_code\":\"NORMAL\",\"observed_at\":\""
                        + observedAt
                        + "\"}"))
        .andExpect(status().isCreated());
  }

  private ResultActions list(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/readings?" + query).header("Authorization", session.bearer()));
  }

  /** Five accepted readings two hours apart, the newest one hour ago. */
  private void fiveReadings(Session session, UUID tank) throws Exception {
    String[] values = {"3.00", "2.90", "2.80", "2.70", "2.60"};
    for (int i = 0; i < values.length; i++) {
      record(
          session, tank, values[i], NOW.minus(1 + 2L * (values.length - 1 - i), ChronoUnit.HOURS));
    }
  }

  @Test
  void theCursorWalksEveryReadingNewestFirstAndEnds() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("list.walker", "OPERATOR", demoAqueduct()));
    fiveReadings(session, tank);

    List<String> values = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      String body =
          list(
                  session,
                  "tank_id=" + tank + "&limit=2" + (cursor == null ? "" : "&cursor=" + cursor))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      values.addAll(
          JsonPath.<List<Object>>read(body, "$.items[*].gauge_value").stream()
              .map(Object::toString)
              .toList());
      cursor = JsonPath.read(body, "$.next_cursor");
      pages++;
    } while (cursor != null);

    assertThat(pages).isEqualTo(3);
    assertThat(values).containsExactly("2.6", "2.7", "2.8", "2.9", "3.0");
  }

  @Test
  void eachItemCarriesItsEffectiveValueAndCorrectionCount() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("list.effective", "OPERATOR", demoAqueduct()));
    record(session, tank, "2.10", NOW.minus(1, ChronoUnit.HOURS));

    list(session, "tank_id=" + tank)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].effective_gauge_value").value(2.10))
        .andExpect(jsonPath("$.items[0].correction_count").value(0))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void filtersByStatusAndObservationRange() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("list.filter", "OPERATOR", demoAqueduct()));
    fiveReadings(session, tank);
    record(session, tank, "0.50", NOW.minus(30, ChronoUnit.MINUTES));

    list(session, "tank_id=" + tank + "&validation_status=FLAGGED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].validation_status").value("FLAGGED"));
    list(
            session,
            "tank_id="
                + tank
                + "&from="
                + NOW.minus(6, ChronoUnit.HOURS)
                + "&to="
                + NOW.minus(2, ChronoUnit.HOURS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void rejectsAnInvertedRangeATamperedCursorAndAnOversizedPage() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("list.invalid", "OPERATOR", demoAqueduct()));
    fiveReadings(session, tank);
    String cursor =
        JsonPath.read(
            list(session, "tank_id=" + tank + "&limit=2")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.next_cursor");

    list(session, "tank_id=" + tank + "&cursor=" + cursor + "x")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
    list(session, "tank_id=" + tank + "&cursor=not-a-cursor")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
    list(session, "tank_id=" + tank + "&limit=300")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    list(session, "tank_id=" + tank + "&from=" + NOW + "&to=" + NOW.minus(1, ChronoUnit.DAYS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    list(session, "tank_id=" + tank + "&validation_status=DELETED")
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownTankOrOneOfAnotherAqueductIsNotFound() throws Exception {
    Session session = login(account("list.isolated", "OPERATOR", demoAqueduct()));
    UUID foreign = newTank(newAqueduct());

    list(session, "tank_id=" + UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    list(session, "tank_id=" + foreign)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void aRoleWithoutReadingViewIsForbidden() throws Exception {
    Session session = login(account("list.support", "SUPPORT_ENTITY", demoAqueduct()));

    list(session, "tank_id=" + newTank(demoAqueduct()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
