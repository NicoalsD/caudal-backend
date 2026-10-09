package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.AppRoleIT;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deny by default and permissions read from the database, with real tokens against the real
 * security chain. The permission cache lives zero seconds here so a change in the matrix shows at
 * once.
 */
@Import(PermissionDeniedIT.ProbeController.class)
@TestPropertySource(properties = "caudal.security.permissions.cache-ttl-seconds=0")
class PermissionDeniedIT extends AppRoleIT {

  private static final String PROBE = "/api/v1/probe/readings";

  @Autowired private MockMvc mvc;

  @TestComponent
  @RestController
  static class ProbeController {

    @GetMapping(PROBE)
    @PreAuthorize("hasAuthority('READING_CREATE')")
    String createReading() {
      return "allowed";
    }
  }

  private UUID user;

  private String bearer(String role, UUID userId) {
    return "Bearer "
        + TestJwts.sign(
            TestJwts.validClaims(userId, role, newAqueduct(), 0, Instant.now()).build(),
            TestJwts.SECRET);
  }

  private UUID newAccount(String name) {
    user = newUser(name, UNUSABLE_HASH);
    return user;
  }

  @Test
  void aRequestWithoutTokenGets401InTheCanonicalFormat() throws Exception {
    mvc.perform(get(PROBE).header("X-Request-Id", "req-401"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("Sesión no válida. Vuelve a iniciar sesión."))
        .andExpect(jsonPath("$.error.request_id").value("req-401"));
  }

  @Test
  void anUnknownPathIsDeniedByDefaultWithoutToken() throws Exception {
    mvc.perform(get("/api/v1/does-not-exist"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void aRoleWithoutThePermissionGets403AndAnEvent() throws Exception {
    UUID account = newAccount("denied.operator");

    mvc.perform(get(PROBE).header("Authorization", bearer("SUPPORT_ENTITY", account)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.error.message").value("No tienes permiso para esta acción."));

    Integer events =
        owner.queryForObject(
            "SELECT count(*) FROM iam.security_events WHERE type = 'PERMISSION_DENIED'"
                + " AND actor_user_id = ?",
            Integer.class,
            account);
    assertThat(events).isEqualTo(1);
  }

  @Test
  void aRoleWithThePermissionPasses() throws Exception {
    UUID account = newAccount("allowed.operator");

    mvc.perform(get(PROBE).header("Authorization", bearer("OPERATOR", account)))
        .andExpect(status().isOk());
  }

  @Test
  void aChangeInTheMatrixTakesEffectWithoutRedeploy() throws Exception {
    UUID account = newAccount("matrix.change");
    String token = bearer("OPERATOR", account);
    mvc.perform(get(PROBE).header("Authorization", token)).andExpect(status().isOk());

    owner.update(
        "DELETE FROM iam.role_permissions WHERE role_code = 'OPERATOR'"
            + " AND permission_code = 'READING_CREATE'");
    try {
      mvc.perform(get(PROBE).header("Authorization", token)).andExpect(status().isForbidden());
    } finally {
      owner.update(
          "INSERT INTO iam.role_permissions (role_code, permission_code)"
              + " VALUES ('OPERATOR', 'READING_CREATE')");
    }
    mvc.perform(get(PROBE).header("Authorization", token)).andExpect(status().isOk());
  }

  @Test
  void anUnknownRoleInATokenHasNoPermissions() throws Exception {
    UUID account = newAccount("ghost.role");

    mvc.perform(get(PROBE).header("Authorization", bearer("GHOST_ROLE", account)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAuthenticatedRequestToAnUnknownPathIs404() throws Exception {
    UUID account = newAccount("unknown.path");

    mvc.perform(get("/api/v1/does-not-exist").header("Authorization", bearer("OPERATOR", account)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void tokensThatAreNotAcceptableGet401() throws Exception {
    UUID account = newAccount("bad.tokens");
    JWTClaimsSet claims =
        TestJwts.validClaims(account, "OPERATOR", UUID.randomUUID(), 0, Instant.now()).build();
    JWTClaimsSet expired =
        new JWTClaimsSet.Builder(claims)
            .issueTime(Date.from(Instant.now().minusSeconds(3600)))
            .expirationTime(Date.from(Instant.now().minusSeconds(1800)))
            .build();
    for (String token :
        List.of(
            TestJwts.unsigned(claims),
            TestJwts.sign(claims, "another-test-only-secret-with-32-bytes-or-more"),
            TestJwts.sign(claims, TestJwts.SECRET.repeat(2), JWSAlgorithm.HS512),
            TestJwts.sign(expired, TestJwts.SECRET),
            "garbage")) {
      mvc.perform(get(PROBE).header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
  }

  @Test
  void aTokenOfAnOlderVersionGets401() throws Exception {
    UUID account = newAccount("old.version");
    String token = bearer("OPERATOR", account);
    owner.update("UPDATE iam.users SET token_version = token_version + 1 WHERE id = ?", account);

    mvc.perform(get(PROBE).header("Authorization", token)).andExpect(status().isUnauthorized());
  }

  @Test
  void anAuthorizationHeaderOf3KiBIsRejectedBeforeParsing() throws Exception {
    mvc.perform(get(PROBE).header("Authorization", "Bearer " + "a".repeat(3 * 1024)))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
  }

  @Test
  void anAuthorizationHeaderOf2KiBGoesOnToTheTokenCheck() throws Exception {
    String header = "Bearer " + "a".repeat(2048 - "Bearer ".length());

    mvc.perform(get(PROBE).header("Authorization", header)).andExpect(status().isUnauthorized());
  }

  @Test
  void healthAndApiDocsArePublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void apiResponsesCarryTheSecurityHeaders() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
  }

  @Test
  void swaggerKeepsItsOwnContentSecurityPolicy() throws Exception {
    mvc.perform(get("/swagger-ui/index.html"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.startsWith("default-src 'self'")));
  }

  @Test
  void corsAnswersOnlyToTheConfiguredOrigins() throws Exception {
    mvc.perform(
            options(PROBE)
                .header("Origin", "https://app.example.test")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

    var foreign =
        mvc.perform(
                options(PROBE)
                    .header("Origin", "https://evil.example.test")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn()
            .getResponse();
    assertThat(foreign.getHeader("Access-Control-Allow-Origin")).isNull();
  }
}
