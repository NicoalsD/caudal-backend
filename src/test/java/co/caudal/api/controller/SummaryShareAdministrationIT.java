package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Authorizing and revoking summaries for entities end to end (docs/API.md, section 13.4-13.5). */
class SummaryShareAdministrationIT extends SessionIT {

  static String body(
      UUID grantee,
      String scope,
      String periodFrom,
      String periodTo,
      String reason,
      String expires) {
    return "{\"grantee_user_id\":\""
        + grantee
        + "\",\"scope\":\""
        + scope
        + "\",\"period_from\":\""
        + periodFrom
        + "\",\"period_to\":\""
        + periodTo
        + "\",\"reason\":\""
        + reason
        + "\",\"expires_at\":\""
        + expires
        + "\"}";
  }

  static final String VALID_REASON = "Informe para la alcaldía municipal.";
  static final String FUTURE = "2030-11-30T23:59:00-05:00";

  ResultActions create(Session session, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/summary-shares")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  ResultActions revoke(Session session, UUID id) throws Exception {
    return mvc.perform(
        delete("/api/v1/summary-shares/" + id).header("Authorization", session.bearer()));
  }

  @Test
  void theBoardAdminAuthorizesAndRevokesWithoutDeletingTheRow() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("share.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("share.admin", "BOARD_ADMIN", aqueduct));

    String response =
        create(
                admin,
                body(
                    entity.userId(),
                    "MONTHLY_SUMMARY",
                    "2026-10-01",
                    "2026-10-31",
                    VALID_REASON,
                    FUTURE))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.grantee_user_id").value(entity.userId().toString()))
            .andExpect(jsonPath("$.scope").value("MONTHLY_SUMMARY"))
            .andExpect(jsonPath("$.period_from").value("2026-10-01"))
            .andExpect(jsonPath("$.authorized_by").value(admin.userId().toString()))
            .andExpect(jsonPath("$.revoked_at").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

    revoke(admin, id).andExpect(status().isNoContent());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM reporting.summary_shares WHERE id = ? AND revoked_at IS NOT"
                    + " NULL AND revoked_by = ?",
                Integer.class,
                id,
                admin.userId()))
        .isEqualTo(1);
    revoke(admin, id)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE action IN ('CREATE_SUMMARY_SHARE',"
                    + " 'REVOKE_SUMMARY_SHARE') AND entity_id = ? AND actor_id = ?",
                Integer.class,
                id.toString(),
                admin.userId()))
        .isEqualTo(2);
  }

  @Test
  void theRulesOfTheAuthorizationAreChecked() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("share.rules.entity", "SUPPORT_ENTITY", aqueduct);
    Account operator = account("share.rules.operator", "OPERATOR", aqueduct);
    Session admin = login(account("share.rules.admin", "BOARD_ADMIN", aqueduct));
    Session board = login(account("share.rules.member", "BOARD_MEMBER", aqueduct));

    create(
            board,
            body(
                entity.userId(),
                "MONTHLY_SUMMARY",
                "2026-10-01",
                "2026-10-31",
                VALID_REASON,
                FUTURE))
        .andExpect(status().isForbidden());
    create(
            admin,
            body(entity.userId(), "MONTHLY_SUMMARY", "2026-10-01", "2026-10-31", "corto", FUTURE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    create(admin, body(entity.userId(), "MONTHLY_SUMMARY", "2026-10-01", "2026-10-31", "", FUTURE))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    create(
            admin,
            body(
                entity.userId(),
                "MONTHLY_SUMMARY",
                "2026-10-31",
                "2026-10-01",
                VALID_REASON,
                FUTURE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create(
            admin,
            body(
                entity.userId(),
                "MONTHLY_SUMMARY",
                "2026-10-01",
                "2026-10-31",
                VALID_REASON,
                "2020-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
    create(
            admin,
            body(
                operator.userId(),
                "MONTHLY_SUMMARY",
                "2026-10-01",
                "2026-10-31",
                VALID_REASON,
                FUTURE))
        .andExpect(status().isBadRequest());
    create(
            admin,
            body(entity.userId(), "EVERYTHING", "2026-10-01", "2026-10-31", VALID_REASON, FUTURE))
        .andExpect(status().isBadRequest());
    create(
            admin,
            body(
                account("share.rules.stranger", "SUPPORT_ENTITY").userId(),
                "MONTHLY_SUMMARY",
                "2026-10-01",
                "2026-10-31",
                VALID_REASON,
                FUTURE))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anotherAqueductCannotRevokeAndUnknownIdsAreNotFound() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("share.other.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("share.other.admin", "BOARD_ADMIN", aqueduct));
    Session stranger = login(account("share.other.stranger", "BOARD_ADMIN"));

    String response =
        create(
                admin,
                body(entity.userId(), "MINUTES", "2026-10-01", "2026-10-31", VALID_REASON, FUTURE))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

    revoke(stranger, id).andExpect(status().isNotFound());
    revoke(admin, UUID.randomUUID()).andExpect(status().isNotFound());
  }
}
