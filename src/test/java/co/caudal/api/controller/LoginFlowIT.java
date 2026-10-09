package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.infrastructure.security.Argon2PasswordHasherAdapter;
import co.caudal.infrastructure.security.Argon2Properties;
import co.caudal.support.AppRoleIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Login end to end: real database, real Argon2id, real tokens, least-privilege role. */
class LoginFlowIT extends AppRoleIT {

  private static final String PASSWORD = "clave-de-prueba-larga";
  private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);

  @Autowired private MockMvc mvc;
  @Autowired private PasswordHasherPort hasher;

  /** Each test calls from its own address, so the per-IP limit of one test cannot hit another. */
  private static RequestPostProcessor freshAddress() {
    String address = "198.51.100." + NEXT_ADDRESS.getAndIncrement();
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }

  private UUID account(String username, String role) {
    UUID user = newUser(username, hasher.hash(NormalizedPassword.of(PASSWORD)));
    if (role != null) {
      newMembership(user, newAqueduct(), role);
    }
    return user;
  }

  private MockHttpServletResponse login(
      String username, String password, RequestPostProcessor address) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .with(address)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andReturn()
        .getResponse();
  }

  private static String errorBodyWithoutRequestId(MockHttpServletResponse response)
      throws Exception {
    return response.getContentAsString().replaceAll("\"request_id\":\"[^\"]*\"", "");
  }

  @Test
  void signsInAndTheAccessTokenOpensMe() throws Exception {
    account("flow.operator", "OPERATOR");

    MockHttpServletResponse response = login("Flow.Operator", PASSWORD, freshAddress());

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString())
        .contains("\"token_type\":\"Bearer\"")
        .contains("\"expires_in\":900");
    String token =
        response.getContentAsString().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("flow.operator"))
        .andExpect(jsonPath("$.memberships[0].role").value("OPERATOR"));
    assertThat(
            owner.queryForObject(
                "SELECT last_login_at IS NOT NULL FROM iam.users WHERE username = 'flow.operator'",
                Boolean.class))
        .isTrue();
  }

  @Test
  void theRefreshTokenIsInTheCookieAndOnlyItsHashIsStored() throws Exception {
    UUID user = account("flow.cookie", "OPERATOR");

    MockHttpServletResponse response = login("flow.cookie", PASSWORD, freshAddress());

    String cookie = response.getHeader("Set-Cookie");
    assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=None");
    assertThat(cookie).contains("Path=/api/v1/auth").contains("Max-Age=2592000");
    String secret = cookie.substring("caudal_rt=".length(), cookie.indexOf(';'));
    assertThat(response.getContentAsString()).doesNotContain(secret);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.refresh_tokens WHERE user_id = ? AND token_hash = ?",
                Integer.class,
                user,
                OpaqueToken.hashOf(secret)))
        .isEqualTo(1);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.refresh_tokens WHERE token_hash = ?",
                Integer.class,
                secret))
        .isZero();
  }

  @Test
  void everyKindOfFailureHasTheSameStatusAndBody() throws Exception {
    account("flow.wrong", "OPERATOR");
    UUID disabled = account("flow.disabled", "OPERATOR");
    owner.update(
        "UPDATE iam.users SET status = 'DISABLED', disabled_at = now() WHERE id = ?", disabled);
    UUID locked = account("flow.locked", "OPERATOR");
    owner.update(
        "UPDATE iam.users SET status = 'LOCKED', locked_until = now() + interval '1 hour' WHERE id = ?",
        locked);
    account("flow.nomember", null);

    List<MockHttpServletResponse> failures =
        List.of(
            login("flow.wrong", "contraseña-incorrecta-1", freshAddress()),
            login("flow.ghost", PASSWORD, freshAddress()),
            login("Not A Username!", PASSWORD, freshAddress()),
            login("flow.disabled", PASSWORD, freshAddress()),
            login("flow.locked", PASSWORD, freshAddress()),
            login("flow.nomember", PASSWORD, freshAddress()));

    String reference = errorBodyWithoutRequestId(failures.getFirst());
    assertThat(reference)
        .contains("\"code\":\"INVALID_CREDENTIALS\"")
        .contains("Usuario o contraseña incorrectos.");
    for (MockHttpServletResponse failure : failures) {
      assertThat(failure.getStatus()).isEqualTo(401);
      assertThat(errorBodyWithoutRequestId(failure)).isEqualTo(reference);
      assertThat(failure.getHeader("Set-Cookie")).isNull();
    }
  }

  @Test
  void fiveConsecutiveFailuresLockTheAccountEvenAgainstTheRightPassword() throws Exception {
    UUID user = account("flow.lockout", "OPERATOR");
    RequestPostProcessor address = freshAddress();

    for (int i = 0; i < 5; i++) {
      assertThat(login("flow.lockout", "contraseña-incorrecta-" + i, address).getStatus())
          .isEqualTo(401);
    }

    Map<String, Object> row =
        owner.queryForMap(
            "SELECT status, locked_until, lockout_level FROM iam.users WHERE id = ?", user);
    assertThat(row.get("status")).isEqualTo("LOCKED");
    assertThat(row.get("locked_until")).isNotNull();
    assertThat(((Number) row.get("lockout_level")).intValue()).isEqualTo(1);
    MockHttpServletResponse afterLock = login("flow.lockout", PASSWORD, freshAddress());
    assertThat(afterLock.getStatus()).isEqualTo(401);
    assertThat(afterLock.getContentAsString()).contains("INVALID_CREDENTIALS");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE type = 'ACCOUNT_LOCKED'"
                    + " AND actor_user_id = ? AND severity = 'MEDIUM'",
                Integer.class,
                user))
        .isEqualTo(1);
  }

  @Test
  void aSuccessfulLoginResetsTheFailureCount() throws Exception {
    UUID user = account("flow.reset", "OPERATOR");
    RequestPostProcessor address = freshAddress();
    for (int i = 0; i < 4; i++) {
      login("flow.reset", "contraseña-incorrecta-" + i, address);
    }
    assertThat(login("flow.reset", PASSWORD, address).getStatus()).isEqualTo(200);

    assertThat(login("flow.reset", "contraseña-incorrecta-x", address).getStatus()).isEqualTo(401);

    assertThat(
            owner.queryForObject("SELECT status FROM iam.users WHERE id = ?", String.class, user))
        .isEqualTo("ACTIVE");
    assertThat(
            owner.queryForObject(
                "SELECT failed_login_count FROM iam.users WHERE id = ?", Integer.class, user))
        .isEqualTo(1);
  }

  @Test
  void attemptsStoreHashesNeverTheAddressOrTheUsername() throws Exception {
    account("flow.privacy", "OPERATOR");
    String address = "198.51.100.200";

    mvc.perform(
            post("/api/v1/auth/login")
                .with(
                    request -> {
                      request.setRemoteAddr(address);
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"flow.privacy\",\"password\":\"contraseña-incorrecta\"}"))
        .andExpect(status().isUnauthorized());

    Map<String, Object> attempt =
        owner.queryForMap(
            "SELECT * FROM iam.login_attempts WHERE user_id = (SELECT id FROM iam.users WHERE"
                + " username = 'flow.privacy') ORDER BY id DESC LIMIT 1");
    assertThat(attempt.get("ip_hmac").toString()).matches("^[0-9a-f]{64}$").doesNotContain(address);
    assertThat(attempt.get("username_hmac").toString())
        .matches("^[0-9a-f]{64}$")
        .doesNotContain("flow.privacy");
    assertThat(attempt.get("failure_reason")).isEqualTo("INVALID_CREDENTIALS");
    assertThat(attempt.get("succeeded")).isEqualTo(false);
  }

  @Test
  void twentyFailuresFromOneAddressBlockTheNextAttempt() throws Exception {
    account("flow.ipblock", "OPERATOR");
    RequestPostProcessor address = freshAddress();

    for (int i = 0; i < 20; i++) {
      assertThat(login("ghost.user." + i, "contraseña-incorrecta", address).getStatus())
          .isEqualTo(401);
    }

    MockHttpServletResponse blocked = login("flow.ipblock", PASSWORD, address);
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");
    assertThat(login("flow.ipblock", PASSWORD, freshAddress()).getStatus()).isEqualTo(200);
  }

  @Test
  void aHashMadeWithOlderParametersIsUpgradedAtLogin() throws Exception {
    String weak =
        new Argon2PasswordHasherAdapter(
                Argon2PasswordHasherAdapter.encoderFor(new Argon2Properties(16, 1, 1, 5)))
            .hash(NormalizedPassword.of(PASSWORD));
    UUID user = newUser("flow.rehash", weak);
    newMembership(user, newAqueduct(), "OPERATOR");

    assertThat(login("flow.rehash", PASSWORD, freshAddress()).getStatus()).isEqualTo(200);

    String stored =
        owner.queryForObject(
            "SELECT password_hash FROM iam.users WHERE id = ?", String.class, user);
    assertThat(stored).startsWith("$argon2id$v=19$m=64,t=1,p=1$").isNotEqualTo(weak);
    assertThat(login("flow.rehash", PASSWORD, freshAddress()).getStatus()).isEqualTo(200);
  }

  @Test
  void theMustChangePasswordFlagIsReported() throws Exception {
    UUID user = account("flow.mustchange", "BOARD_ADMIN");
    owner.update("UPDATE iam.users SET must_change_password = true WHERE id = ?", user);

    MockHttpServletResponse response = login("flow.mustchange", PASSWORD, freshAddress());

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString()).contains("\"must_change_password\":true");
    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=604800");
  }
}
