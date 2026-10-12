package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entities see the summaries the Board shares with them, end to end (docs/API.md, section 13.6;
 * docs/Reglas-de-negocio.md, section 16.3): create, view as support entity, revoke, then not
 * visible, and another aqueduct isolated.
 */
class SummaryShareIT extends SessionIT {

  private static final String FUTURE = "2030-11-30T23:59:00-05:00";
  private static final String REASON = "Informe para la alcaldía municipal.";

  /** One incident inside October, so the summary of that month counts one observed record. */
  void observedRecordInOctober(UUID aqueduct) {
    owner.update(
        "INSERT INTO ops.incidents (aqueduct_id, source, category_code, description, status,"
            + " tracking_code_hash, reported_at) VALUES (?, 'OPERATOR', 'LEAK', 'Gotea el tubo"
            + " junto a la escuela.', 'REPORTED', encode(sha256(convert_to(gen_random_uuid()::text,"
            + " 'UTF8')), 'hex'), ?)",
        aqueduct,
        OffsetDateTime.ofInstant(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
  }

  UUID grant(Session admin, UUID grantee, String scope) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/summary-shares")
                    .header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"grantee_user_id\":\""
                            + grantee
                            + "\",\"scope\":\""
                            + scope
                            + "\",\"period_from\":\"2026-10-01\",\"period_to\":\"2026-10-31\","
                            + "\"reason\":\""
                            + REASON
                            + "\",\"expires_at\":\""
                            + FUTURE
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
  }

  ResultActions list(Session session, String query) throws Exception {
    return mvc.perform(get("/api/v1/summaries" + query).header("Authorization", session.bearer()));
  }

  @Test
  void anEntityViewsTheCountsOfItsSummaryAndTheReadIsLogged() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    observedRecordInOctober(aqueduct);
    Account entity = account("summary.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("summary.admin", "BOARD_ADMIN", aqueduct));
    UUID share = grant(admin, entity.userId(), "MONTHLY_SUMMARY");
    Session support = login(entity);

    list(support, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].summary_share_id").value(share.toString()))
        .andExpect(jsonPath("$.items[0].scope").value("MONTHLY_SUMMARY"))
        .andExpect(jsonPath("$.items[0].period_from").value("2026-10-01"))
        .andExpect(jsonPath("$.items[0].content.observed").value(1))
        .andExpect(jsonPath("$.items[0].content.estimated").value(0))
        .andExpect(jsonPath("$.items[0].reason").doesNotExist())
        .andExpect(jsonPath("$.items[0].authorized_by").doesNotExist())
        .andExpect(jsonPath("$.items[0].summary_share_id").isString());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.data_access_log WHERE resource = 'SUMMARY'"
                    + " AND summary_share_id = ? AND user_id = ?",
                Integer.class,
                share,
                entity.userId()))
        .isEqualTo(1);
  }

  @Test
  void aPeriodOutsideTheShareAndAHalfPeriodAreHandled() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("summary.period.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("summary.period.admin", "BOARD_ADMIN", aqueduct));
    grant(admin, entity.userId(), "MONTHLY_SUMMARY");
    Session support = login(entity);

    list(support, "?period_from=2026-10-05&period_to=2026-10-06")
        .andExpect(jsonPath("$.items.length()").value(1));
    list(support, "?period_from=2026-09-01&period_to=2026-09-30")
        .andExpect(jsonPath("$.items.length()").value(0));
    list(support, "?period_from=2026-10-05")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void aRevokedOrExpiredShareIsNotVisibleAndAnOperatorIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("summary.revoke.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("summary.revoke.admin", "BOARD_ADMIN", aqueduct));
    UUID share = grant(admin, entity.userId(), "MONTHLY_SUMMARY");
    Session support = login(entity);

    list(support, "").andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(delete("/api/v1/summary-shares/" + share).header("Authorization", admin.bearer()))
        .andExpect(status().isNoContent());
    list(support, "").andExpect(jsonPath("$.items.length()").value(0));

    owner.update(
        "INSERT INTO reporting.summary_shares (aqueduct_id, grantee_user_id, scope, period_from,"
            + " period_to, reason, authorized_by, expires_at) VALUES (?, ?, 'MONTHLY_SUMMARY', ?, ?,"
            + " 'Autorización ya vencida.', ?, ?)",
        aqueduct,
        entity.userId(),
        LocalDate.parse("2026-10-01"),
        LocalDate.parse("2026-10-31"),
        admin.userId(),
        OffsetDateTime.ofInstant(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
    list(support, "").andExpect(jsonPath("$.items.length()").value(0));

    Session operator = login(account("summary.revoke.operator", "OPERATOR", aqueduct));
    list(operator, "").andExpect(status().isForbidden());
  }

  @Test
  void aMinutesShareNeedsAFinalMinutesOfThePeriod() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Account entity = account("summary.minutes.entity", "SUPPORT_ENTITY", aqueduct);
    Session admin = login(account("summary.minutes.admin", "BOARD_ADMIN", aqueduct));
    grant(admin, entity.userId(), "MINUTES");
    Session support = login(entity);

    list(support, "").andExpect(jsonPath("$.items.length()").value(0));

    String minutes =
        mvc.perform(
                post("/api/v1/minutes")
                    .header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"period_from\":\"2026-10-01\",\"period_to\":\"2026-10-31\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID minutesId = UUID.fromString(minutes.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    list(support, "").andExpect(jsonPath("$.items.length()").value(0));

    mvc.perform(
            post("/api/v1/minutes/" + minutesId + "/finalize")
                .header("Authorization", admin.bearer()))
        .andExpect(status().isOk());
    list(support, "")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].scope").value("MINUTES"));
  }

  @Test
  void anotherAqueductIsIsolatedAndTheBoardSeesItsOwnShares() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID other = newAqueductWithRules();
    Account entity = account("summary.iso.entity", "SUPPORT_ENTITY", aqueduct);
    Account outsider = account("summary.iso.outsider", "SUPPORT_ENTITY", other);
    Session admin = login(account("summary.iso.admin", "BOARD_ADMIN", aqueduct));
    Session otherAdmin = login(account("summary.iso.other", "BOARD_ADMIN", other));
    UUID share = grant(admin, entity.userId(), "MONTHLY_SUMMARY");

    list(login(entity), "").andExpect(jsonPath("$.items.length()").value(1));
    list(login(outsider), "").andExpect(jsonPath("$.items.length()").value(0));
    list(otherAdmin, "").andExpect(jsonPath("$.items.length()").value(0));
    list(admin, "")
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].summary_share_id").value(share.toString()));
  }
}
