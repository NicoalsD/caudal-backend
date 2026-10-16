package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Change of password end to end, with real hashes and the least-privilege role. */
class ChangePasswordIT extends SessionIT {

  private static final String NEW_PASSWORD = "una-clave-nueva-bien-larga";

  private ResultActions change(Session session, String current, String next) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/change-password")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"current_password\":\"" + current + "\",\"new_password\":\"" + next + "\"}"));
  }

  @Test
  void changesThePasswordEndsTheSessionsAndTheNewOneSignsIn() throws Exception {
    Account account = account("change.ok", "OPERATOR");
    owner.update("UPDATE iam.users SET must_change_password = true WHERE id = ?", account.userId());
    Session session = login(account);
    Session otherDevice = login(account);

    change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/auth/current-user").header("Authorization", otherDevice.bearer()))
        .andExpect(status().isUnauthorized());
    mvc.perform(refreshWith(otherDevice.refreshSecret())).andExpect(status().isUnauthorized());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.refresh_tokens WHERE user_id = ?"
                    + " AND revoked_reason = 'PASSWORD_CHANGED'",
                Integer.class,
                account.userId()))
        .isEqualTo(2);
    assertThat(
            owner.queryForMap(
                "SELECT token_version, must_change_password, password_changed_at IS NOT NULL AS"
                    + " changed FROM iam.users WHERE id = ?",
                account.userId()))
        .containsEntry("token_version", 1)
        .containsEntry("must_change_password", false)
        .containsEntry("changed", true);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.password_history WHERE user_id = ?",
                Integer.class,
                account.userId()))
        .isEqualTo(1);
    Session again = login(account, NEW_PASSWORD);
    mvc.perform(get("/api/v1/auth/current-user").header("Authorization", again.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void theOldPasswordNoLongerSignsIn() throws Exception {
    Account account = account("change.old", "OPERATOR");
    Session session = login(account);
    change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"change.old\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void aWrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
    Account account = account("change.wrong", "OPERATOR");
    Session session = login(account);

    change(session, "no-es-la-clave-actual", NEW_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/v1/auth/current-user").header("Authorization", session.bearer()))
        .andExpect(status().isOk());
    assertThat(
            owner.queryForObject(
                "SELECT token_version FROM iam.users WHERE id = ?",
                Integer.class,
                account.userId()))
        .isZero();
  }

  @Test
  void aWeakPasswordIsRefusedWithoutBurningTheSession() throws Exception {
    Account account = account("change.weak", "OPERATOR");
    Session session = login(account);

    change(session, PASSWORD, "corta-11-ca")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"))
        .andExpect(jsonPath("$.error.details.rule").value("TOO_SHORT"));
    change(session, PASSWORD, "contraseña1234")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.details.rule").value("COMMON"));
    change(session, PASSWORD, "mi-change.weak-clave")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.details.rule").value("CONTAINS_USERNAME"));

    mvc.perform(get("/api/v1/auth/current-user").header("Authorization", session.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void aRecentPasswordCannotBeReusedButAnOlderOneCan() throws Exception {
    Account account = account("change.history", "OPERATOR");
    String[] passwords = {
      PASSWORD,
      "segunda-clave-larga-2",
      "tercera-clave-larga-3",
      "cuarta-clave-larga-4",
      "quinta-clave-larga-5",
      "sexta-clave-larga-6"
    };
    for (int i = 1; i < passwords.length; i++) {
      Session session = login(account, passwords[i - 1]);
      change(session, passwords[i - 1], passwords[i]).andExpect(status().isNoContent());
    }
    Session current = login(account, passwords[5]);

    change(current, passwords[5], passwords[2])
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.details.rule").value("RECENTLY_USED"));
    change(current, passwords[5], passwords[5])
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.details.rule").value("RECENTLY_USED"));
    change(current, passwords[5], passwords[0]).andExpect(status().isNoContent());
  }

  @Test
  void anAccountMarkedToChangeItsPasswordCanStillCallTheEndpoint() throws Exception {
    Account account = account("change.first", "OPERATOR");
    Session session = login(account);
    owner.update("UPDATE iam.users SET must_change_password = true WHERE id = ?", account.userId());

    change(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
  }
}
