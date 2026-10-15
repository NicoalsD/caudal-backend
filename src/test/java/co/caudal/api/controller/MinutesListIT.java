package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Listing the minutes of the aqueduct end to end (docs/API.md, section 13.8). */
class MinutesListIT extends SessionIT {

  static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

  ResultActions list(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/minutes" + query).header("Authorization", session.bearer()));
  }

  /** Generates a minutes for a period that ends {@code daysAgo} days before today. */
  String generate(Session session, int daysAgo) throws Exception {
    LocalDate day = TODAY.minusDays(daysAgo);
    String body =
        mvc.perform(
                post("/api/v1/minutes")
                    .header("Authorization", session.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"period_from\":\"" + day + "\",\"period_to\":\"" + day + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void listsTheNewestMinutesFirstWithoutSections() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.list.member", "BOARD_MEMBER", aqueduct));
    String older = generate(member, 3);
    String newer = generate(member, 2);

    list(member, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(newer))
        .andExpect(jsonPath("$.items[0].period_from").value(TODAY.minusDays(2).toString()))
        .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
        .andExpect(jsonPath("$.items[0].generated_at").isString())
        .andExpect(jsonPath("$.items[0].finalized_at").doesNotExist())
        .andExpect(jsonPath("$.items[0].pdf_sha256").doesNotExist())
        .andExpect(jsonPath("$.items[0].content").doesNotExist())
        .andExpect(jsonPath("$.items[1].id").value(older))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void aFinalMinutesShowsItsFinalizationAndPdfHash() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.list.final", "BOARD_MEMBER", aqueduct));
    String id = generate(member, 1);
    mvc.perform(
            post("/api/v1/minutes/" + id + "/finalize").header("Authorization", member.bearer()))
        .andExpect(status().isOk());

    list(member, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].status").value("FINAL"))
        .andExpect(jsonPath("$.items[0].finalized_at").isString())
        .andExpect(jsonPath("$.items[0].pdf_sha256").isString());
  }

  @Test
  void pagesWithASignedCursorWithoutRepeatingRows() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.list.pages", "BOARD_MEMBER", aqueduct));
    String first = generate(member, 4);
    String second = generate(member, 3);
    String third = generate(member, 2);

    String firstPage =
        list(member, "?limit=2")
            .andExpect(jsonPath("$.items[0].id").value(third))
            .andExpect(jsonPath("$.items[1].id").value(second))
            .andExpect(jsonPath("$.next_cursor").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(firstPage, "$.next_cursor");
    list(member, "?limit=2&cursor=" + cursor)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(first))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    String last = cursor.substring(cursor.length() - 1);
    String altered = cursor.substring(0, cursor.length() - 1) + ("A".equals(last) ? "B" : "A");
    list(member, "?limit=2&cursor=" + altered)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void limitOutOfRangeIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.list.limit", "BOARD_MEMBER", aqueduct));

    list(member, "?limit=0").andExpect(status().isBadRequest());
    list(member, "?limit=101").andExpect(status().isBadRequest());
  }

  @Test
  void operatorIsRefusedAndOtherAqueductsAreHidden() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.list.owner", "BOARD_MEMBER", aqueduct));
    generate(member, 1);
    Session operator = login(account("minutes.list.operator", "OPERATOR", aqueduct));
    Session stranger = login(account("minutes.list.stranger", "BOARD_MEMBER"));

    list(operator, "").andExpect(status().isForbidden());
    list(stranger, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  }
}
