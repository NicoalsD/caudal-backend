package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Reading the security events end to end (docs/API.md, section 18.2). */
class SecurityEventListIT extends SessionIT {

  ResultActions read(Session session, String query) throws Exception {
    return mvc.perform(
        get("/api/v1/security-events" + query).header("Authorization", session.bearer()));
  }

  /** Inserts one event as the owner, which is how the writers of the table store them. */
  void event(UUID aqueduct, String type, String severity, UUID actor, String at, String details) {
    owner.update(
        "INSERT INTO iam.security_events (aqueduct_id, type, severity, actor_user_id, ip_hmac,"
            + " request_id, details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
        aqueduct,
        type,
        severity,
        actor,
        "a".repeat(64),
        "req-" + type,
        details,
        OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC));
  }

  @Test
  void theBoardSeesItsOwnEventsWithoutAddresses() throws Exception {
    Account admin = account("security.list.admin", "BOARD_ADMIN");
    Account intruder = account("security.list.intruder", "OPERATOR");
    event(
        admin.aqueductId(),
        "PERMISSION_DENIED",
        "MEDIUM",
        intruder.userId(),
        "2026-10-01T10:00:00Z",
        "{\"action\":\"UPDATE_USER\",\"password\":\"no-debe-salir\"}");
    event(
        admin.aqueductId(),
        "ACCOUNT_LOCKED",
        "HIGH",
        intruder.userId(),
        "2026-10-02T10:00:00Z",
        "{}");

    read(login(admin), "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].type").value("ACCOUNT_LOCKED"))
        .andExpect(jsonPath("$.items[0].request_id").value("req-ACCOUNT_LOCKED"))
        .andExpect(jsonPath("$.items[0].actor_user_id").value(intruder.userId().toString()))
        .andExpect(jsonPath("$.items[0].ip_hmac").doesNotExist())
        .andExpect(jsonPath("$.items[1].details.password").value("[redactado]"))
        .andExpect(jsonPath("$.items[1].details.action").value("UPDATE_USER"));
  }

  @Test
  void otherAqueductsAndGlobalEventsAreNeverReturned() throws Exception {
    Account admin = account("security.list.own", "BOARD_ADMIN");
    Account stranger = account("security.list.other", "BOARD_ADMIN");
    event(stranger.aqueductId(), "RATE_LIMITED", "LOW", null, "2026-10-01T10:00:00Z", "{}");
    event(null, "ACCOUNT_LOCKED", "HIGH", null, "2026-10-01T11:00:00Z", "{}");
    event(admin.aqueductId(), "RATE_LIMITED", "LOW", null, "2026-10-01T12:00:00Z", "{}");

    read(login(admin), "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].type").value("RATE_LIMITED"));
    read(login(stranger), "").andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void theFiltersAndTheCursorNarrowAndPage() throws Exception {
    Account admin = account("security.list.filter", "BOARD_ADMIN");
    UUID actor = admin.userId();
    event(admin.aqueductId(), "MFA_FAILED", "MEDIUM", actor, "2026-10-01T10:00:00Z", "{}");
    event(admin.aqueductId(), "MFA_FAILED", "CRITICAL", actor, "2026-10-02T10:00:00Z", "{}");
    event(admin.aqueductId(), "NONCE_REPLAY", "LOW", actor, "2026-10-03T10:00:00Z", "{}");
    Session board = login(admin);

    read(board, "?type=MFA_FAILED").andExpect(jsonPath("$.items.length()").value(2));
    read(board, "?type=MFA_FAILED&severity=CRITICAL")
        .andExpect(jsonPath("$.items.length()").value(1));
    read(board, "?from=2026-10-02T00:00:00Z&to=2026-10-02T23:59:59Z")
        .andExpect(jsonPath("$.items.length()").value(1));
    read(board, "?type=FOO").andExpect(status().isBadRequest());
    read(board, "?severity=URGENT").andExpect(status().isBadRequest());
    read(board, "?from=2026-10-03T00:00:00Z&to=2026-10-01T00:00:00Z")
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    ResultActions first = read(board, "?limit=2");
    first.andExpect(jsonPath("$.items.length()").value(2));
    String cursor =
        JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.next_cursor");
    read(board, "?limit=2&cursor=" + cursor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].severity").value("MEDIUM"))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
    read(board, "?limit=2&cursor=" + cursor + "x")
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void theReadsAreLoggedAndOperatorsAreRefused() throws Exception {
    Account admin = account("security.list.log", "BOARD_ADMIN");
    Account operator = account("security.list.worker", "OPERATOR", admin.aqueductId());
    event(admin.aqueductId(), "RATE_LIMITED", "LOW", null, "2026-10-01T10:00:00Z", "{}");

    read(login(operator), "").andExpect(status().isForbidden());
    read(login(admin), "").andExpect(status().isOk());

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.data_access_log WHERE aqueduct_id = ?"
                    + " AND resource = 'SECURITY_EVENTS' AND user_id = ?",
                Integer.class,
                admin.aqueductId(),
                admin.userId()))
        .isEqualTo(1);
  }
}
