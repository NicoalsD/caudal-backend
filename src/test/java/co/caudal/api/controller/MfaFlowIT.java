package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.domain.mfa.Totp;
import co.caudal.domain.mfa.TotpSecret;
import co.caudal.support.SessionIT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** TOTP end to end: enrollment, confirmation, login with the code and anti-repetition. */
class MfaFlowIT extends SessionIT {

  private TotpSecret enroll(Session session) throws Exception {
    String body =
        mvc.perform(post("/api/v1/auth/mfa/enroll").header("Authorization", session.bearer()))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.otpauth_uri")
                    .value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return TotpSecret.fromBase32(body.replaceAll(".*\"secret\":\"([A-Z2-7]+)\".*", "$1"));
  }

  private ResultActions confirm(Session session, String code) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/mfa/confirm")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + code + "\"}"));
  }

  private ResultActions loginWith(String username, String mfaCode) throws Exception {
    String extra = mfaCode == null ? "" : ",\"mfa_code\":\"" + mfaCode + "\"";
    return mvc.perform(
        post("/api/v1/auth/login")
            .with(
                request -> {
                  request.setRemoteAddr("192.0.2." + (1 + (int) (System.nanoTime() % 200)));
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"username\":\""
                    + username
                    + "\",\"password\":\""
                    + PASSWORD
                    + "\""
                    + extra
                    + "}"));
  }

  @Test
  void aConfirmedFactorGuardsTheLoginAndEachCodeWorksOnce() throws Exception {
    Account admin = account("mfa.admin", "BOARD_ADMIN");
    Session session = login(admin);
    TotpSecret secret = enroll(session);
    long step = Totp.stepAt(Instant.now());

    confirm(session, Totp.codeAt(secret, step)).andExpect(status().isNoContent());
    assertThat(
            owner.queryForObject(
                "SELECT confirmed_at IS NOT NULL FROM iam.mfa_factors WHERE user_id = ?",
                Boolean.class,
                admin.userId()))
        .isTrue();

    loginWith("mfa.admin", null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("MFA_REQUIRED"));
    loginWith("mfa.admin", Totp.codeAt(secret, step))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    String next = Totp.codeAt(secret, step + 1);
    loginWith("mfa.admin", next).andExpect(status().isOk());
    loginWith("mfa.admin", next)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE type = 'MFA_FAILED'"
                    + " AND actor_user_id = ?",
                Integer.class,
                admin.userId()))
        .isEqualTo(2);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.login_attempts WHERE failure_reason = 'MFA_FAILED'"
                    + " AND user_id = ?",
                Integer.class,
                admin.userId()))
        .isEqualTo(2);
  }

  @Test
  void aWrongPasswordNeverRevealsThatACodeIsNeeded() throws Exception {
    Account admin = account("mfa.hidden", "BOARD_ADMIN");
    Session session = login(admin);
    TotpSecret secret = enroll(session);
    confirm(session, Totp.codeAt(secret, Totp.stepAt(Instant.now())))
        .andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mfa.hidden\",\"password\":\"otra-clave-incorrecta\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void anUnconfirmedFactorIsReplacedAndAConfirmedOneIsAConflict() throws Exception {
    Session session = login(account("mfa.again", "PROJECT_TEAM"));
    TotpSecret first = enroll(session);
    TotpSecret second = enroll(session);
    long step = Totp.stepAt(Instant.now());

    assertThat(second).isNotEqualTo(first);
    confirm(session, Totp.codeAt(first, step))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    confirm(session, Totp.codeAt(second, step)).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/mfa/enroll").header("Authorization", session.bearer()))
        .andExpect(status().isConflict());
    confirm(session, Totp.codeAt(second, step + 1)).andExpect(status().isConflict());
  }

  @Test
  void anOperatorCannotEnrollAndConfirmingWithoutEnrollmentIsNotFound() throws Exception {
    Session operator = login(account("mfa.operator", "OPERATOR"));
    mvc.perform(post("/api/v1/auth/mfa/enroll").header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());

    Session admin = login(account("mfa.none", "BOARD_ADMIN"));
    confirm(admin, "123456").andExpect(status().isNotFound());
    confirm(admin, "12345").andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/auth/mfa/enroll")).andExpect(status().isUnauthorized());
  }
}
