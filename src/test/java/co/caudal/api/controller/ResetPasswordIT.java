package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.domain.user.ResetCode;
import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Setting a password with a one-time code, end to end, as the least-privilege role. */
class ResetPasswordIT extends SessionIT {

  private static final String NEW_PASSWORD = "una-clave-nueva-bien-larga";

  private ResetCode issue(UUID userId, String expiry) {
    ResetCode code = ResetCode.generate();
    UUID issuer =
        owner.queryForObject(
            "SELECT id FROM iam.users WHERE username = 'caudal.system'", UUID.class);
    owner.update(
        "INSERT INTO iam.password_reset_grants (user_id, code_hash, issued_by, expires_at)"
            + " VALUES (?, ?, ?, now() + CAST(? AS interval))",
        userId,
        code.hash(),
        issuer,
        expiry);
    return code;
  }

  private ResultActions reset(String username, String code, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"username\":\""
                    + username
                    + "\",\"code\":\""
                    + code
                    + "\",\"new_password\":\""
                    + password
                    + "\"}"));
  }

  @Test
  void theCodeSetsTheNewPasswordOnceAndEndsOldSessions() throws Exception {
    Account account = account("reset.ok", "OPERATOR");
    Session before = login(account);
    ResetCode code = issue(account.userId(), "1 hour");

    reset("reset.ok", code.display().toLowerCase(), NEW_PASSWORD).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/me").header("Authorization", before.bearer()))
        .andExpect(status().isUnauthorized());
    mvc.perform(refreshWith(before.refreshSecret())).andExpect(status().isUnauthorized());
    Session after = login(account, NEW_PASSWORD);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", after.bearer()))
        .andExpect(status().isOk());
    assertThat(
            owner.queryForObject(
                "SELECT used_at IS NOT NULL FROM iam.password_reset_grants WHERE user_id = ?",
                Boolean.class,
                account.userId()))
        .isTrue();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.password_history WHERE user_id = ?",
                Integer.class,
                account.userId()))
        .isEqualTo(1);
    reset("reset.ok", code.display(), "otra-clave-bastante-larga")
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aNewAccountKeepsTheFlagUntilItsFirstChange() throws Exception {
    Account account = account("reset.flag", "OPERATOR");
    owner.update("UPDATE iam.users SET must_change_password = true WHERE id = ?", account.userId());
    ResetCode code = issue(account.userId(), "1 hour");

    reset("reset.flag", code.display(), NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(
            owner.queryForObject(
                "SELECT must_change_password FROM iam.users WHERE id = ?",
                Boolean.class,
                account.userId()))
        .isTrue();
  }

  @Test
  void expiredUnknownAndForeignCodesAreAllTheSame401() throws Exception {
    Account account = account("reset.bad", "OPERATOR");
    Account other = account("reset.other", "OPERATOR");
    ResetCode expired = issue(account.userId(), "-1 minute");
    ResetCode foreign = issue(other.userId(), "1 hour");

    String reference =
        reset("reset.bad", expired.display(), NEW_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll("\"request_id\":\"[^\"]*\"", "");
    for (String[] attempt :
        new String[][] {
          {"reset.bad", ResetCode.generate().display()},
          {"reset.bad", foreign.display()},
          {"reset.ghost", foreign.display()},
          {"Not A Username!", foreign.display()},
          {"reset.bad", "short"}
        }) {
      String body =
          reset(attempt[0], attempt[1], NEW_PASSWORD)
              .andExpect(status().isUnauthorized())
              .andReturn()
              .getResponse()
              .getContentAsString()
              .replaceAll("\"request_id\":\"[^\"]*\"", "");
      assertThat(body).isEqualTo(reference);
    }
  }

  @Test
  void aWeakPasswordDoesNotBurnTheCode() throws Exception {
    Account account = account("reset.weak", "OPERATOR");
    ResetCode code = issue(account.userId(), "1 hour");

    reset("reset.weak", code.display(), "contraseña1234")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));
    assertThat(
            owner.queryForObject(
                "SELECT used_at IS NULL FROM iam.password_reset_grants WHERE user_id = ?",
                Boolean.class,
                account.userId()))
        .isTrue();

    reset("reset.weak", code.display(), NEW_PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void aRecentPasswordCannotBeSetBack() throws Exception {
    Account account = account("reset.history", "OPERATOR");
    ResetCode code = issue(account.userId(), "1 hour");

    reset("reset.history", code.display(), PASSWORD)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.details.rule").value("RECENTLY_USED"));
  }

  @Test
  void aDisabledAccountCannotUseItsCode() throws Exception {
    Account account = account("reset.disabled", "OPERATOR");
    ResetCode code = issue(account.userId(), "1 hour");
    owner.update(
        "UPDATE iam.users SET status = 'DISABLED', disabled_at = now() WHERE id = ?",
        account.userId());

    reset("reset.disabled", code.display(), NEW_PASSWORD).andExpect(status().isUnauthorized());
  }
}
