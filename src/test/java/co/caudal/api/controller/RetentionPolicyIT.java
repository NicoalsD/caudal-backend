package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Changing the retention periods end to end (docs/API.md, section 18.3). */
class RetentionPolicyIT extends SessionIT {

  ResultActions change(Session session, String dataClass, String body) throws Exception {
    return mvc.perform(
        put("/api/v1/retention-policies/" + dataClass)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void theBoardChangesThePeriodAndTheChangeIsAudited() throws Exception {
    Account admin = account("retention.board", "BOARD_ADMIN");
    Session board = login(admin);

    change(board, "TELEMETRY_POINTS", "{\"retention_days\":365}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data_class").value("TELEMETRY_POINTS"))
        .andExpect(jsonPath("$.retention_days").value(365))
        .andExpect(jsonPath("$.updated_by").value(admin.userId().toString()))
        .andExpect(jsonPath("$.updated_at").isNotEmpty());

    assertThat(
            owner.queryForObject(
                "SELECT retention_days FROM audit.retention_policies WHERE data_class ="
                    + " 'TELEMETRY_POINTS'",
                Integer.class))
        .isEqualTo(365);
    assertThat(
            owner.queryForMap(
                "SELECT action, entity_type, entity_id, after_state->>'retention_days' AS days"
                    + " FROM audit.audit_log WHERE aqueduct_id = ? AND action ="
                    + " 'RETENTION_POLICY_CHANGED'",
                admin.aqueductId()))
        .containsEntry("entity_type", "retention_policy")
        .containsEntry("entity_id", "TELEMETRY_POINTS")
        .containsEntry("days", "365");
    change(board, "TELEMETRY_POINTS", "{\"retention_days\":730}").andExpect(status().isOk());
  }

  @Test
  void onlyTheBoardAdministratorMayChangeIt() throws Exception {
    Account admin = account("retention.admin", "BOARD_ADMIN");
    Session team = login(account("retention.team", "PROJECT_TEAM", admin.aqueductId()));
    Session member = login(account("retention.member", "BOARD_MEMBER", admin.aqueductId()));
    Session operator = login(account("retention.operator", "OPERATOR", admin.aqueductId()));

    change(team, "SECURITY_EVENTS", "{\"retention_days\":30}").andExpect(status().isForbidden());
    change(member, "SECURITY_EVENTS", "{\"retention_days\":30}").andExpect(status().isForbidden());
    change(operator, "SECURITY_EVENTS", "{\"retention_days\":30}")
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownClassesAndOutOfRangeDaysAreRefused() throws Exception {
    Session board = login(account("retention.checks", "BOARD_ADMIN"));

    change(board, "PASSWORDS", "{\"retention_days\":30}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    change(board, "SECURITY_EVENTS", "{\"retention_days\":0}").andExpect(status().isBadRequest());
    change(board, "SECURITY_EVENTS", "{\"retention_days\":3651}")
        .andExpect(status().isBadRequest());
    change(board, "SECURITY_EVENTS", "{\"retention_days\":\"mucho\"}")
        .andExpect(status().isBadRequest());
    change(board, "SECURITY_EVENTS", "{}").andExpect(status().isBadRequest());
    change(board, "SECURITY_EVENTS", "{\"retention_days\":30,\"extra\":1}")
        .andExpect(status().isBadRequest());
  }
}
