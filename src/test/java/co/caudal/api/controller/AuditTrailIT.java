package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The command bus writes security changes to the hash-chained audit log. */
class AuditTrailIT extends SessionIT {

  @Test
  void anAdministrativeChangeIsChainedInTheAuditLogOfTheAqueduct() throws Exception {
    Account admin = account("audit.admin", "BOARD_ADMIN");
    Account operator = account("audit.operator", "OPERATOR", admin.aqueductId());
    Session session = login(admin);

    for (String status : new String[] {"LOCKED", "ACTIVE"}) {
      mvc.perform(
              patch("/api/v1/users/" + operator.userId())
                  .header("Authorization", session.bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"" + status + "\"}"))
          .andExpect(status().isOk());
    }

    Map<String, Object> last =
        owner.queryForMap(
            "SELECT actor_type, actor_id, action, entity_type, entity_id, request_id, ip_hmac"
                + " FROM audit.audit_log WHERE aqueduct_id = ? ORDER BY id DESC LIMIT 1",
            admin.aqueductId());
    assertThat(last)
        .containsEntry("actor_type", "USER")
        .containsEntry("actor_id", admin.userId())
        .containsEntry("action", "UPDATE_USER")
        .containsEntry("entity_type", "user")
        .containsEntry("entity_id", operator.userId().toString());
    assertThat(last.get("request_id")).isNotNull();
    assertThat(last.get("ip_hmac")).isNotNull();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE aqueduct_id = ?",
                Integer.class,
                admin.aqueductId()))
        .isEqualTo(2);
    assertThat(owner.queryForObject("SELECT audit.verify_chain(?)", Long.class, admin.aqueductId()))
        .as("verify_chain returns the first broken row, or null when the chain is whole")
        .isNull();
  }
}
