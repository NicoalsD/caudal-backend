package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Listing the incidents of the aqueduct end to end (docs/API.md, section 12.1). */
class IncidentListIT extends SessionIT {

  private static final String REPORTER_HMAC = "b".repeat(64);

  /** Inserts an incident as the owner, with a random tracking hash and, for the public, an HMAC. */
  UUID incident(UUID aqueduct, UUID sector, String source, String status, Instant reportedAt) {
    return owner.queryForObject(
        "INSERT INTO ops.incidents (aqueduct_id, source, category_code, sector_id, description,"
            + " location_hint, status, tracking_code_hash, reporter_ip_hmac, reported_at)"
            + " VALUES (?, ?, 'LEAK', ?, 'Gotea el tubo junto a la escuela.', 'Frente a la"
            + " escuela', ?, encode(sha256(convert_to(gen_random_uuid()::text, 'UTF8')), 'hex'),"
            + " ?, ?) RETURNING id",
        UUID.class,
        aqueduct,
        source,
        sector,
        status,
        "PUBLIC_FORM".equals(source) ? REPORTER_HMAC : null,
        OffsetDateTime.ofInstant(reportedAt, ZoneOffset.UTC));
  }

  UUID sector(UUID aqueduct, String code) {
    return owner.queryForObject(
        "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = ?",
        UUID.class,
        aqueduct,
        code);
  }

  ResultActions list(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/incidents" + query).header("Authorization", session.bearer()));
  }

  @Test
  void aTeamMemberSeesIncidentsWithoutTheReporterData() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id =
        incident(aqueduct, null, "PUBLIC_FORM", "REPORTED", Instant.parse("2026-10-01T10:00:00Z"));
    Session team = login(account("incident.team", "PROJECT_TEAM", aqueduct));

    String body =
        list(team, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(id.toString()))
            .andExpect(jsonPath("$.items[0].source").value("PUBLIC_FORM"))
            .andExpect(jsonPath("$.items[0].status").value("REPORTED"))
            .andExpect(jsonPath("$.items[0].reporter_ip_hmac").doesNotExist())
            .andExpect(jsonPath("$.items[0].tracking_code_hash").doesNotExist())
            .andExpect(jsonPath("$.next_cursor").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trackingHash =
        owner.queryForObject(
            "SELECT tracking_code_hash FROM ops.incidents WHERE id = ?", String.class, id);
    assertThat(body).doesNotContain(REPORTER_HMAC).doesNotContain(trackingHash);
  }

  @Test
  void filtersByStatusSourceSectorAndReportDate() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID alto = sector(aqueduct, "ALTO");
    incident(aqueduct, null, "PUBLIC_FORM", "REPORTED", Instant.parse("2026-10-01T10:00:00Z"));
    UUID confirmed =
        incident(aqueduct, alto, "OPERATOR", "CONFIRMED", Instant.parse("2026-10-02T10:00:00Z"));
    incident(aqueduct, alto, "ANOMALY", "REPORTED", Instant.parse("2026-10-03T10:00:00Z"));
    Session team = login(account("incident.filters", "PROJECT_TEAM", aqueduct));

    list(team, "?status=REPORTED").andExpect(jsonPath("$.items.length()").value(2));
    list(team, "?source=OPERATOR")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(confirmed.toString()));
    list(team, "?sector_id=" + alto).andExpect(jsonPath("$.items.length()").value(2));
    list(team, "?from=2026-10-02T00:00:00Z&to=2026-10-02T23:59:59Z")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(confirmed.toString()));
    list(team, "?from=2026-10-05T00:00:00Z&to=2026-10-01T00:00:00Z")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    list(team, "?status=FOO").andExpect(status().isBadRequest());
  }

  @Test
  void pagesWithASignedCursorWithoutRepeatingRows() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID first =
        incident(aqueduct, null, "OPERATOR", "REPORTED", Instant.parse("2026-10-01T10:00:00Z"));
    UUID second =
        incident(aqueduct, null, "OPERATOR", "REPORTED", Instant.parse("2026-10-02T10:00:00Z"));
    UUID third =
        incident(aqueduct, null, "OPERATOR", "REPORTED", Instant.parse("2026-10-03T10:00:00Z"));
    Session team = login(account("incident.pages", "PROJECT_TEAM", aqueduct));

    String firstPage =
        list(team, "?limit=2")
            .andExpect(jsonPath("$.items[0].id").value(third.toString()))
            .andExpect(jsonPath("$.items[1].id").value(second.toString()))
            .andExpect(jsonPath("$.next_cursor").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = firstPage.replaceAll(".*\"next_cursor\":\"([^\"]+)\".*", "$1");
    list(team, "?limit=2&cursor=" + cursor)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(first.toString()))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());

    String last = cursor.substring(cursor.length() - 1);
    String altered = cursor.substring(0, cursor.length() - 1) + ("A".equals(last) ? "B" : "A");
    list(team, "?limit=2&cursor=" + altered)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void supportEntitiesAreRefusedAndOtherAqueductsAreHidden() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    incident(aqueduct, null, "OPERATOR", "REPORTED", Instant.parse("2026-10-01T10:00:00Z"));
    Session support = login(account("incident.support", "SUPPORT_ENTITY", aqueduct));
    Session stranger = login(account("incident.stranger", "BOARD_MEMBER"));

    list(support, "").andExpect(status().isForbidden());
    list(stranger, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  }
}
