package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Reading the audit log end to end (docs/API.md, section 18.1). */
class AuditLogIT extends SessionIT {

  ResultActions read(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/audit-log" + query).header("Authorization", session.bearer()));
  }

  /** Makes two audited changes on the operator: each one is one UPDATE_USER entry. */
  void changeOperatorStatus(Session admin, Account operator) throws Exception {
    for (String status : new String[] {"LOCKED", "ACTIVE"}) {
      mvc.perform(
              patch("/api/v1/users/" + operator.userId())
                  .header("Authorization", admin.bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"" + status + "\"}"))
          .andExpect(status().isOk());
    }
  }

  @Test
  void theBoardReadsTheLogWithoutRequestIdsOrAddresses() throws Exception {
    Account admin = account("audit.log.admin", "BOARD_ADMIN");
    Account operator = account("audit.log.operator", "OPERATOR", admin.aqueductId());
    Session board = login(admin);
    changeOperatorStatus(board, operator);
    Session team = login(account("audit.log.team", "PROJECT_TEAM", admin.aqueductId()));

    read(team, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].action").value("UPDATE_USER"))
        .andExpect(jsonPath("$.items[0].actor_id").value(admin.userId().toString()))
        .andExpect(jsonPath("$.items[0].entity_id").value(operator.userId().toString()))
        .andExpect(jsonPath("$.items[0].row_hash").isNotEmpty())
        .andExpect(jsonPath("$.items[0].request_id").doesNotExist())
        .andExpect(jsonPath("$.items[0].ip_hmac").doesNotExist())
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  void theFiltersNarrowTheLog() throws Exception {
    Account admin = account("audit.log.filter", "BOARD_ADMIN");
    Account operator = account("audit.log.filtered", "OPERATOR", admin.aqueductId());
    Session board = login(admin);
    changeOperatorStatus(board, operator);

    read(board, "?action=UPDATE_USER&entity_type=user&actor_id=" + admin.userId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    read(board, "?action=RULESET_ACTIVATED").andExpect(jsonPath("$.items.length()").value(0));
    read(board, "?actor_id=" + UUID.randomUUID()).andExpect(jsonPath("$.items.length()").value(0));
    read(board, "?action=update_user").andExpect(status().isBadRequest());
    read(board, "?from=2030-01-02T00:00:00Z&to=2030-01-01T00:00:00Z")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void theCursorPagesThroughTheLogWithoutRepeats() throws Exception {
    Account admin = account("audit.log.pages", "BOARD_ADMIN");
    Account operator = account("audit.log.paged", "OPERATOR", admin.aqueductId());
    Session board = login(admin);
    changeOperatorStatus(board, operator);

    ResultActions first = read(board, "?limit=1");
    first.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    String cursor =
        JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.next_cursor");
    String firstId =
        JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.items[0].id")
            .toString();

    ResultActions second = read(board, "?limit=1&cursor=" + cursor);
    second
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
    String secondId =
        JsonPath.read(second.andReturn().getResponse().getContentAsString(), "$.items[0].id")
            .toString();
    assertThat(secondId).isNotEqualTo(firstId);

    read(board, "?limit=1&cursor=" + cursor + "x").andExpect(status().isBadRequest());
    read(board, "?limit=1&cursor=" + cursor + "x")
        .andExpect(jsonPath("$.error.code").value("CURSOR_INVALID"));
  }

  @Test
  void eachQueryIsWrittenToTheDataAccessLog() throws Exception {
    Account admin = account("audit.log.access", "BOARD_ADMIN");
    Session board = login(admin);

    read(board, "").andExpect(status().isOk());
    read(board, "?limit=5").andExpect(status().isOk());

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.data_access_log WHERE aqueduct_id = ?"
                    + " AND user_id = ? AND resource = 'AUDIT_LOG' AND resource_id IS NULL",
                Integer.class,
                admin.aqueductId(),
                admin.userId()))
        .isEqualTo(2);
  }

  @Test
  void otherAqueductsAndOtherRolesAreRefused() throws Exception {
    Account admin = account("audit.log.own", "BOARD_ADMIN");
    Account operator = account("audit.log.worker", "OPERATOR", admin.aqueductId());
    Session board = login(admin);
    changeOperatorStatus(board, operator);
    Session stranger = login(account("audit.log.stranger", "BOARD_ADMIN"));

    read(login(operator), "").andExpect(status().isForbidden());
    read(stranger, "").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  }
}
