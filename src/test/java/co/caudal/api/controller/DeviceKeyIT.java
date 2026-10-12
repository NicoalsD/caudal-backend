package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Public keys of the devices end to end: registration, rotation, revocation and the credential
 * lookup the signature check uses (docs/API.md, section 16.5 and 16.8).
 */
class DeviceKeyIT extends SessionIT {

  /** Seconds in the default rotation period of seven days. */
  private static final double SEVEN_DAYS = 7 * 24 * 60 * 60;

  /** Tolerance of the rotation period, in seconds. */
  private static final double TOLERANCE = 120;

  private static final String URL_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

  /** The application role: no session scope, so only the lookup function sees the devices. */
  @Autowired private JdbcClient app;

  private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    return mvc.perform(
        request
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String idOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private static String keyIdOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"key_id\":\"([^\"]+)\".*", "$1");
  }

  /** A new Ed25519 public key: the last 32 bytes of the JDK's X.509 encoding. */
  private static byte[] newPublicKey() throws Exception {
    byte[] encoded =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
    return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
  }

  private static String encode(byte[] raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private static String keyBody(String publicKey) {
    return "{\"public_key\":\"" + publicKey + "\"}";
  }

  private UUID gateway(Session session) throws Exception {
    return UUID.fromString(
        idOf(
            send(session, post("/api/v1/devices"), "{\"kind\":\"GATEWAY\",\"name\":\"Gateway\"}")
                .andExpect(status().isCreated())));
  }

  private ResultActions register(Session session, UUID device, byte[] raw) throws Exception {
    return send(session, post("/api/v1/devices/" + device + "/keys"), keyBody(encode(raw)));
  }

  private double secondsUntilRevoked(UUID key) {
    return owner.queryForObject(
        "SELECT extract(epoch FROM (revoked_at - now())) FROM devices.device_keys WHERE id = ?",
        Double.class,
        key);
  }

  @Test
  void registersAKeyAndReturnsItsFingerprint() throws Exception {
    Session session = login(account("keys.register", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    byte[] raw = newPublicKey();

    ResultActions result =
        register(session, device, raw)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key_id").isNotEmpty())
            .andExpect(jsonPath("$.activated_at").isNotEmpty());
    String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    result.andExpect(jsonPath("$.fingerprint").value(fingerprint));

    UUID key = UUID.fromString(keyIdOf(result));
    assertThat(
            owner.queryForObject(
                "SELECT revoked_at IS NULL FROM devices.device_keys WHERE id = ?",
                Boolean.class,
                key))
        .isTrue();
  }

  @Test
  void rotationRevokesThePreviousKeyAboutSevenDaysAhead() throws Exception {
    Session session = login(account("keys.rotate", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    UUID first = UUID.fromString(keyIdOf(register(session, device, newPublicKey())));
    UUID second =
        UUID.fromString(
            keyIdOf(register(session, device, newPublicKey()).andExpect(status().isCreated())));

    assertThat(secondsUntilRevoked(first))
        .isBetween(SEVEN_DAYS - TOLERANCE, SEVEN_DAYS + TOLERANCE);
    assertThat(
            owner.queryForObject(
                "SELECT revoked_at IS NULL FROM devices.device_keys WHERE id = ?",
                Boolean.class,
                second))
        .isTrue();

    assertThat(
            app.sql("SELECT key_id FROM devices.find_device_credentials(:device)")
                .param("device", device)
                .query(UUID.class)
                .list())
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  void theSameKeyTwiceIsAConflictAndTheRotationIsRolledBack() throws Exception {
    Session session = login(account("keys.duplicate", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    UUID first = UUID.fromString(keyIdOf(register(session, device, newPublicKey())));
    byte[] second = newPublicKey();
    UUID secondKey =
        UUID.fromString(keyIdOf(register(session, device, second).andExpect(status().isCreated())));

    register(session, device, second).andExpect(status().isConflict());

    assertThat(
            owner.queryForObject(
                "SELECT revoked_at IS NULL FROM devices.device_keys WHERE id = ?",
                Boolean.class,
                secondKey))
        .isTrue();
    assertThat(secondsUntilRevoked(first))
        .isBetween(SEVEN_DAYS - TOLERANCE, SEVEN_DAYS + TOLERANCE);
  }

  @Test
  void aKeyAlreadyRegisteredOnAnotherDeviceIsAConflict() throws Exception {
    Session session = login(account("keys.other", "BOARD_ADMIN", newAqueductWithRules()));
    byte[] raw = newPublicKey();
    register(session, gateway(session), raw).andExpect(status().isCreated());

    register(session, gateway(session), raw).andExpect(status().isConflict());
  }

  @Test
  void aKeyThatIsNotAnEd25519PointIsUnprocessable() throws Exception {
    Session session = login(account("keys.invalid", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    byte[] raw = newPublicKey();
    String valid = encode(raw);
    byte[] allOnes = new byte[32];
    Arrays.fill(allOnes, (byte) 0xff);
    allOnes[31] = 0x7f;
    char last = valid.charAt(42);
    String nonCanonical =
        valid.substring(0, 42) + URL_ALPHABET.charAt(URL_ALPHABET.indexOf(last) ^ 1);

    for (String key :
        new String[] {"abc", valid + "=", "+".repeat(43), encode(allOnes), nonCanonical}) {
      send(session, post("/api/v1/devices/" + device + "/keys"), keyBody(key))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.error.code").value("INVALID_KEY"));
    }
    send(session, post("/api/v1/devices/" + device + "/keys"), "{}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRevokedKeyCannotBeRevokedAgainAndLeavesTheLookup() throws Exception {
    Session session = login(account("keys.delete", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    UUID key = UUID.fromString(keyIdOf(register(session, device, newPublicKey())));

    send(session, delete("/api/v1/devices/" + device + "/keys/" + key), "")
        .andExpect(status().isNoContent());
    send(session, delete("/api/v1/devices/" + device + "/keys/" + key), "")
        .andExpect(status().isConflict());

    assertThat(secondsUntilRevoked(key)).isLessThanOrEqualTo(0);
    assertThat(
            app.sql("SELECT key_id FROM devices.find_device_credentials(:device)")
                .param("device", device)
                .query(UUID.class)
                .list())
        .hasSize(1)
        .containsNull();
  }

  @Test
  void revokingTheKeyInGracePeriodStopsItAtOnce() throws Exception {
    Session session = login(account("keys.grace", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    UUID previous = UUID.fromString(keyIdOf(register(session, device, newPublicKey())));
    UUID current =
        UUID.fromString(
            keyIdOf(register(session, device, newPublicKey()).andExpect(status().isCreated())));

    send(session, delete("/api/v1/devices/" + device + "/keys/" + previous), "")
        .andExpect(status().isNoContent());

    assertThat(secondsUntilRevoked(previous)).isLessThanOrEqualTo(0);
    assertThat(
            app.sql("SELECT key_id FROM devices.find_device_credentials(:device)")
                .param("device", device)
                .query(UUID.class)
                .list())
        .containsExactly(current);
  }

  @Test
  void aKeyOfAnotherDeviceOrAForeignDeviceIsNotFound() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("keys.isolation", "BOARD_ADMIN", aqueduct));
    UUID device = gateway(session);
    UUID otherDevice = gateway(session);
    UUID key = UUID.fromString(keyIdOf(register(session, device, newPublicKey())));
    UUID foreign =
        owner.queryForObject(
            "INSERT INTO devices.devices (aqueduct_id, kind, name, created_by)"
                + " VALUES (?, 'GATEWAY', 'Gateway ajeno', (SELECT id FROM iam.users"
                + " WHERE username = 'caudal.system')) RETURNING id",
            UUID.class,
            newAqueductWithRules());

    send(session, delete("/api/v1/devices/" + otherDevice + "/keys/" + key), "")
        .andExpect(status().isNotFound());
    send(session, delete("/api/v1/devices/" + device + "/keys/" + UUID.randomUUID()), "")
        .andExpect(status().isNotFound());
    register(session, foreign, newPublicKey()).andExpect(status().isNotFound());
    send(session, delete("/api/v1/devices/" + foreign + "/keys/" + key), "")
        .andExpect(status().isNotFound());
  }

  @Test
  void anOperatorCannotManageKeys() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account("keys.admin", "BOARD_ADMIN", aqueduct));
    UUID device = gateway(admin);
    UUID key = UUID.fromString(keyIdOf(register(admin, device, newPublicKey())));
    Session operator = login(account("keys.operator", "OPERATOR", aqueduct));

    register(operator, device, newPublicKey()).andExpect(status().isForbidden());
    send(operator, delete("/api/v1/devices/" + device + "/keys/" + key), "")
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyOneKeyPerDeviceCanBeActiveInTheDatabase() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("keys.index", "BOARD_ADMIN", aqueduct));
    UUID device = gateway(session);
    register(session, device, newPublicKey()).andExpect(status().isCreated());

    assertThatThrownBy(
            () ->
                owner.update(
                    "INSERT INTO devices.device_keys (aqueduct_id, device_id, public_key,"
                        + " fingerprint) VALUES (?, ?, ?, ?)",
                    aqueduct,
                    device,
                    encode(newPublicKey()),
                    "0".repeat(64)))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void theApplicationRoleSeesNoDevicesOrKeysOutsideTheLookup() throws Exception {
    Session session = login(account("keys.lookup", "BOARD_ADMIN", newAqueductWithRules()));
    UUID device = gateway(session);
    register(session, device, newPublicKey()).andExpect(status().isCreated());

    assertThat(app.sql("SELECT count(*) FROM devices.devices").query(Long.class).single()).isZero();
    assertThat(app.sql("SELECT count(*) FROM devices.device_keys").query(Long.class).single())
        .isZero();
    assertThat(
            app.sql("SELECT aqueduct_id FROM devices.find_device_credentials(:device)")
                .param("device", device)
                .query(UUID.class)
                .list())
        .hasSize(1);
  }
}
