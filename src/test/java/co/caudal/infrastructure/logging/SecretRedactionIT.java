package co.caudal.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Secrets never reach the logs or the HTTP error bodies. Each test runs the real flow, then checks
 * the captured console output (the structured log lines) and the response body: the plain
 * passwords, the access and refresh tokens, the device key material and the signatures must be
 * absent, and the error bodies must not expose stack traces, SQL, class names or the JDBC URL.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretRedactionIT extends SessionIT {

  private static final String PATH = "/api/v1/devices/telemetry";
  private static final String WRONG_PASSWORD = "clave-equivocada-muy-larga-1";
  private static final String NEW_PASSWORD = "clave-nueva-muy-larga-2";
  private static final String RESET_PASSWORD = "clave-reinicio-muy-larga-3";

  /** Configured secrets of the test profile, which must never leave the process. */
  private static final String JWT_SECRET = "test-only-jwt-secret-with-at-least-32-bytes-0000";

  private static final String ROLE_PASSWORD = "test-only-role-password";
  private static final String IP_HMAC_KEY = "test-only-ip-hmac-key-with-at-least-32-bytes";

  /** Fragments that only an unhandled error or the framework would print. */
  private static final List<String> INTERNALS =
      List.of(
          "co.caudal.",
          "org.springframework",
          "org.postgresql",
          "jdbc:",
          "SQLException",
          "StackTrace",
          "Exception",
          "SELECT ",
          "INSERT ",
          "UPDATE ",
          ROLE_PASSWORD);

  @Autowired private Clock clock;

  private static String nonce() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static String base64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private static void assertNoneOf(String text, List<String> secrets, String where) {
    for (String secret : secrets) {
      assertThat(text).as("%s must not contain %s", where, secret).doesNotContain(secret);
    }
  }

  @Test
  void aWrongPasswordIsNeitherLoggedNorEchoed(CapturedOutput output) throws Exception {
    login(account("secrets.wrong", "OPERATOR"));

    ResultActions failed =
        mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"secrets.wrong\",\"password\":\"" + WRONG_PASSWORD + "\"}"));
    failed.andExpect(status().isUnauthorized());

    assertNoneOf(responseBody(failed), List.of(WRONG_PASSWORD, PASSWORD), "login body");
    assertNoneOf(output.getAll(), List.of(WRONG_PASSWORD, PASSWORD), "log output");
  }

  @Test
  void changingThePasswordNeverLogsOrEchoesIt(CapturedOutput output) throws Exception {
    Session session = login(account("secrets.change", "OPERATOR"));

    ResultActions wrongCurrent =
        mvc.perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"current_password\":\""
                        + WRONG_PASSWORD
                        + "\",\"new_password\":\""
                        + NEW_PASSWORD
                        + "\"}"));
    wrongCurrent.andExpect(status().isUnauthorized());
    assertNoneOf(responseBody(wrongCurrent), List.of(WRONG_PASSWORD, NEW_PASSWORD), "error body");

    ResultActions changed =
        mvc.perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"current_password\":\""
                        + PASSWORD
                        + "\",\"new_password\":\""
                        + NEW_PASSWORD
                        + "\"}"));
    changed.andExpect(status().isNoContent());

    assertNoneOf(output.getAll(), List.of(PASSWORD, WRONG_PASSWORD, NEW_PASSWORD), "log output");
  }

  @Test
  void resettingThePasswordWithAWrongCodeNeverLogsOrEchoesIt(CapturedOutput output)
      throws Exception {
    account("secrets.reset", "OPERATOR");

    ResultActions reset =
        mvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"secrets.reset\",\"code\":\"000000\",\"new_password\":\""
                        + RESET_PASSWORD
                        + "\"}"));
    assertThat(reset.andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);

    assertNoneOf(responseBody(reset), List.of(RESET_PASSWORD, "000000"), "error body");
    assertNoneOf(output.getAll(), List.of(RESET_PASSWORD), "log output");
  }

  @Test
  void theAccessTokenAndTheRefreshTokenAreNeverLogged(CapturedOutput output) throws Exception {
    Session session = login(account("secrets.tokens", "OPERATOR"));
    List<String> tokens = List.of(session.accessToken(), session.refreshSecret());

    MockHttpServletResponse refreshed =
        mvc.perform(refreshWith(session.refreshSecret()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String rotated = secretOf(refreshed);
    assertNoneOf(output.getAll(), tokens, "log output after login and refresh");

    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", session.bearer())
                .cookie(new Cookie("caudal_rt", rotated))
                .header("X-Requested-With", "caudal-web")
                .header("Origin", ORIGIN))
        .andExpect(status().isNoContent());

    assertNoneOf(
        output.getAll(),
        List.of(session.accessToken(), session.refreshSecret(), rotated),
        "log output");
  }

  @Test
  void deviceKeyMaterialAndSignaturesNeverLeakIntoLogsOrErrors(CapturedOutput output)
      throws Exception {
    Session session = login(account("secrets.device", "BOARD_ADMIN", newAqueductWithRules()));
    UUID tank = newTank(session.aqueductId());
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    String privateKey = base64(pair.getPrivate().getEncoded());
    String publicKey = DeviceRequestSigner.publicKeyText(pair);

    ResultActions created =
        mvc.perform(
                post("/api/v1/devices")
                    .header("Authorization", session.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Sensor de secretos\",\"tank_id\":\""
                            + tank
                            + "\"}"))
            .andExpect(status().isCreated());
    UUID device = UUID.fromString(idOf(responseBody(created)));

    mvc.perform(
            post("/api/v1/devices/" + device + "/keys")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"public_key\":\"" + publicKey + "\"}"))
        .andExpect(status().isCreated());

    String body = "{\"points\":[]}";
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    String nonce = nonce();
    String signed =
        DeviceRequestSigner.sign(
            pair, "POST", PATH, timestamp, nonce, body.getBytes(StandardCharsets.UTF_8));
    String tampered = "{\"points\":[{\"x\":1}]}";
    ResultActions rejected =
        mvc.perform(
            post(PATH)
                .header("X-Device-Id", device.toString())
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signed)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tampered));
    rejected
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));

    List<String> material = List.of(privateKey, publicKey, signed);
    assertNoneOf(responseBody(rejected), material, "error body");
    assertNoneOf(output.getAll(), material, "log output");
  }

  @Test
  void errorBodiesNeverShowStackTracesSqlClassNamesOrTheDatabaseUrl(CapturedOutput output)
      throws Exception {
    ResultActions malformed =
        mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"x\", \"password\": "));
    ResultActions unknownRoute = mvc.perform(get("/api/v1/no-existe/" + UUID.randomUUID()));
    ResultActions unknownField =
        mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"password\":\"y\",\"\":{\"a\":[}"));

    for (ResultActions result : List.of(malformed, unknownRoute, unknownField)) {
      String body = responseBody(result);
      assertThat(body).doesNotContain("\"trace\"");
      assertNoneOf(body, INTERNALS, "error body");
    }
    assertNoneOf(output.getAll(), List.of(ROLE_PASSWORD, JWT_SECRET, IP_HMAC_KEY), "log output");
  }

  @Test
  void theActuatorEnvAndConfigpropsAreNotExposed() throws Exception {
    Session session = login(account("secrets.actuator", "BOARD_ADMIN", newAqueductWithRules()));

    for (String path : List.of("/actuator/env", "/actuator/configprops")) {
      ResultActions anonymous = mvc.perform(get(path));
      ResultActions authenticated =
          mvc.perform(get(path).header("Authorization", session.bearer()));
      for (ResultActions result : List.of(anonymous, authenticated)) {
        int status = result.andReturn().getResponse().getStatus();
        assertThat(status).as(path).isNotEqualTo(200);
        assertNoneOf(responseBody(result), List.of(JWT_SECRET, ROLE_PASSWORD, IP_HMAC_KEY), path);
      }
    }
  }

  private static String idOf(String json) {
    return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }
}
