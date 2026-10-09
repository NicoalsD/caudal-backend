package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.support.AppRoleIT;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Login, refresh, rotation and reuse detection end to end, as the least-privilege role. */
class RefreshFlowIT extends AppRoleIT {

  private static final String PASSWORD = "clave-de-prueba-larga";
  private static final String ORIGIN = "https://app.example.test";
  private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(100);

  @Autowired private MockMvc mvc;
  @Autowired private PasswordHasherPort hasher;

  private UUID account(String username, String role) {
    UUID user = newUser(username, hasher.hash(NormalizedPassword.of(PASSWORD)));
    newMembership(user, newAqueduct(), role);
    return user;
  }

  private String loginCookieSecret(String username) throws Exception {
    String address = "198.51.100." + NEXT_ADDRESS.getAndIncrement();
    MockHttpServletResponse response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(
                        request -> {
                          request.setRemoteAddr(address);
                          return request;
                        })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return secretOf(response);
  }

  private static String secretOf(MockHttpServletResponse response) {
    String cookie = response.getHeader("Set-Cookie");
    return cookie.substring("caudal_rt=".length(), cookie.indexOf(';'));
  }

  private static MockHttpServletRequestBuilder refreshWith(String secret) {
    return post("/api/v1/auth/refresh")
        .cookie(new Cookie("caudal_rt", secret))
        .header("X-Requested-With", "caudal-web")
        .header("Origin", ORIGIN);
  }

  @Test
  void refreshGivesANewAccessTokenThatWorksAndRotatesTheCookie() throws Exception {
    account("refresh.ok", "OPERATOR");
    String first = loginCookieSecret("refresh.ok");

    MockHttpServletResponse response =
        mvc.perform(refreshWith(first)).andExpect(status().isOk()).andReturn().getResponse();

    String second = secretOf(response);
    assertThat(second).isNotEqualTo(first);
    assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly").contains("Secure");
    String token =
        response.getContentAsString().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("refresh.ok"));
    mvc.perform(refreshWith(second)).andExpect(status().isOk());
  }

  @Test
  void reusingARotatedTokenRevokesTheFamilyAndRecordsTheEvent() throws Exception {
    UUID user = account("refresh.reuse", "OPERATOR");
    String first = loginCookieSecret("refresh.reuse");
    String second = secretOf(mvc.perform(refreshWith(first)).andReturn().getResponse());

    mvc.perform(refreshWith(first))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));

    mvc.perform(refreshWith(second))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class,
                user))
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT revoked_reason FROM iam.refresh_tokens WHERE token_hash = ?",
                String.class,
                OpaqueToken.hashOf(second)))
        .isEqualTo("REUSE_DETECTED");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.security_events WHERE type = 'TOKEN_REUSE_DETECTED'"
                    + " AND severity = 'HIGH' AND actor_user_id = ?",
                Integer.class,
                user))
        .isEqualTo(1);
  }

  @Test
  void withoutTheClientHeaderOrWithAForeignOriginIs403() throws Exception {
    account("refresh.csrf", "OPERATOR");
    String secret = loginCookieSecret("refresh.csrf");

    mvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie("caudal_rt", secret))
                .header("Origin", ORIGIN))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie("caudal_rt", secret))
                .header("X-Requested-With", "caudal-web")
                .header("Origin", "https://evil.example.test"))
        .andExpect(status().isForbidden());
    mvc.perform(refreshWith(secret)).andExpect(status().isOk());
  }

  @Test
  void withoutCookieOrWithAnUnknownOneIs401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/refresh")
                .header("X-Requested-With", "caudal-web")
                .header("Origin", ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    mvc.perform(refreshWith(OpaqueToken.generate().raw()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void aDisabledAccountCannotRefresh() throws Exception {
    UUID user = account("refresh.disabled", "OPERATOR");
    String secret = loginCookieSecret("refresh.disabled");
    owner.update(
        "UPDATE iam.users SET status = 'DISABLED', disabled_at = now() WHERE id = ?", user);

    mvc.perform(refreshWith(secret)).andExpect(status().isUnauthorized());
  }

  @Test
  void aTokenVersionBumpedAfterLoginIsReflectedInTheRefreshedToken() throws Exception {
    UUID user = account("refresh.version", "BOARD_MEMBER");
    String secret = loginCookieSecret("refresh.version");
    owner.update("UPDATE iam.users SET token_version = token_version + 1 WHERE id = ?", user);

    MockHttpServletResponse response =
        mvc.perform(refreshWith(secret)).andExpect(status().isOk()).andReturn().getResponse();

    String token =
        response.getContentAsString().replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
