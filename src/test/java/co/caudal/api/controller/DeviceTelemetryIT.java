package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Telemetry of a level sensor end to end: real signed requests against the API (docs/API.md,
 * section 16.1 and docs/Protocolo-de-dispositivos.md, sections 2 to 4).
 */
class DeviceTelemetryIT extends SessionIT {

  private static final String PATH = "/api/v1/devices/telemetry";

  /** A tank of the test aqueduct has its gauge between 0.00 and 5.00. */
  private static final String OUT_OF_RANGE = "999.00";

  /** Bytes of a body over the 256 KiB limit of the telemetry route. */
  private static final int OVER_LIMIT = 256 * 1024 + 1;

  @Autowired private Clock clock;

  /** A sensor with its key: the id, the key and the session of its aqueduct. */
  private record Sensor(UUID device, UUID key, KeyPair pair, Session session) {}

  private Sensor sensor(String username) throws Exception {
    Session session = login(account(username, "BOARD_ADMIN", newAqueductWithRules()));
    UUID tank = newTank(session.aqueductId());
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    ResultActions created =
        send(
                session,
                post("/api/v1/devices"),
                "{\"kind\":\"LEVEL_SENSOR\",\"name\":\"Sensor principal\",\"tank_id\":\""
                    + tank
                    + "\"}")
            .andExpect(status().isCreated());
    UUID device = UUID.fromString(idOf(created, "id"));
    ResultActions key =
        send(
                session,
                post("/api/v1/devices/" + device + "/keys"),
                "{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}")
            .andExpect(status().isCreated());
    return new Sensor(device, UUID.fromString(idOf(key, "key_id")), pair, session);
  }

  private ResultActions send(Session session, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    return mvc.perform(
        request
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String idOf(ResultActions result, String field) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"" + field + "\":\"([^\"]+)\".*", "$1");
  }

  private static String nonce() {
    byte[] bytes = new byte[16];
    new java.security.SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * A signed request for the sensor: the signature covers {@code signed}, the body sent is {@code
   * sent}.
   */
  private ResultActions signedPost(
      Sensor sensor, long timestamp, String nonce, byte[] signed, byte[] sent) throws Exception {
    String text = Long.toString(timestamp);
    String signature = DeviceRequestSigner.sign(sensor.pair(), "POST", PATH, text, nonce, signed);
    return mvc.perform(
        post(PATH)
            .header("X-Device-Id", sensor.device().toString())
            .header("X-Timestamp", text)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content(sent));
  }

  private ResultActions signed(Sensor sensor, String body) throws Exception {
    return signedPost(sensor, clock.instant().getEpochSecond(), nonce(), bytes(body), bytes(body));
  }

  /** A batch: a level point of 2.10 and a battery point, both observed a minute ago. */
  private String batch(UUID level, UUID battery, String levelValue) {
    String observed = clock.instant().minusSeconds(60).toString();
    return "{\"points\":["
        + "{\"id\":\""
        + level
        + "\",\"metric\":\"LEVEL\",\"value\":"
        + levelValue
        + ",\"observed_at\":\""
        + observed
        + "\"},"
        + "{\"id\":\""
        + battery
        + "\",\"metric\":\"BATTERY_V\",\"value\":12.4,\"observed_at\":\""
        + observed
        + "\"}]}";
  }

  private long count(String sql, Object... args) {
    return owner.queryForObject(sql, Long.class, args);
  }

  @Test
  void aValidBatchStoresTheLevelAsASensorReading() throws Exception {
    Sensor sensor = sensor("telemetry.valid");
    UUID level = UUID.randomUUID();
    UUID battery = UUID.randomUUID();

    signed(sensor, batch(level, battery, "2.10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(2))
        .andExpect(jsonPath("$.duplicates").value(0))
        .andExpect(jsonPath("$.rejected").value(0))
        .andExpect(jsonPath("$.results[0].id").value(level.toString()))
        .andExpect(jsonPath("$.results[0].status").value("ACCEPTED"))
        .andExpect(jsonPath("$.results[0].reading_id").value(level.toString()))
        .andExpect(jsonPath("$.results[0].validation_status").value("ACCEPTED"))
        .andExpect(jsonPath("$.results[1].reading_id").doesNotExist());

    assertThat(
            owner.queryForMap(
                "SELECT source, device_id, recorded_by, water_appearance_code"
                    + " FROM ops.readings WHERE id = ?",
                level))
        .containsEntry("source", "SENSOR")
        .containsEntry("device_id", sensor.device())
        .containsEntry("water_appearance_code", "NOT_OBSERVED");
    assertThat(
            count(
                "SELECT count(*) FROM devices.telemetry_points WHERE device_id = ? AND reading_id = ?",
                sensor.device(),
                level))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM devices.devices WHERE id = ? AND last_seen_at IS NOT NULL",
                sensor.device()))
        .isEqualTo(1);
  }

  @Test
  void theSameBatchWithTheSameNonceIsAReplay() throws Exception {
    Sensor sensor = sensor("telemetry.replay");
    byte[] body = bytes(batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"));
    String nonce = nonce();
    long now = clock.instant().getEpochSecond();
    signedPost(sensor, now, nonce, body, body).andExpect(status().isOk());

    signedPost(sensor, now, nonce, body, body)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("NONCE_REPLAY"));
  }

  @Test
  void theSameBatchWithANewNonceComesBackAsDuplicates() throws Exception {
    Sensor sensor = sensor("telemetry.duplicate");
    UUID level = UUID.randomUUID();
    UUID battery = UUID.randomUUID();
    String body = batch(level, battery, "2.10");
    signed(sensor, body).andExpect(status().isOk());
    long readings = count("SELECT count(*) FROM ops.readings WHERE device_id = ?", sensor.device());

    signed(sensor, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(0))
        .andExpect(jsonPath("$.duplicates").value(2))
        .andExpect(jsonPath("$.results[0].status").value("DUPLICATE"))
        .andExpect(jsonPath("$.results[0].reading_id").value(level.toString()));

    assertThat(count("SELECT count(*) FROM ops.readings WHERE device_id = ?", sensor.device()))
        .isEqualTo(readings);
  }

  @Test
  void aBodyChangedByOneByteIsAnInvalidSignature() throws Exception {
    Sensor sensor = sensor("telemetry.altered");
    String body = batch(UUID.randomUUID(), UUID.randomUUID(), "2.10");
    String sent = body.replace("12.4", "12.5");
    long now = clock.instant().getEpochSecond();

    signedPost(sensor, now, nonce(), bytes(body), bytes(sent))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
  }

  @Test
  void aTimestampThreeHundredAndOneSecondsOffIsOutOfWindow() throws Exception {
    Sensor sensor = sensor("telemetry.clock");
    byte[] body = bytes(batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"));

    signedPost(sensor, clock.instant().getEpochSecond() - 301, nonce(), body, body)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("TIMESTAMP_OUT_OF_WINDOW"));
  }

  @Test
  void aRevokedKeyIsAnInvalidSignature() throws Exception {
    Sensor sensor = sensor("telemetry.revoked");
    send(
            sensor.session(),
            delete("/api/v1/devices/" + sensor.device() + "/keys/" + sensor.key()),
            "")
        .andExpect(status().isNoContent());

    signed(sensor, batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
  }

  @Test
  void aSuspendedDeviceIsForbidden() throws Exception {
    Sensor sensor = sensor("telemetry.suspended");
    send(
            sensor.session(),
            patch("/api/v1/devices/" + sensor.device()),
            "{\"status\":\"SUSPENDED\"}")
        .andExpect(status().isOk());

    signed(sensor, batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("DEVICE_INACTIVE"));
  }

  @Test
  void anUnknownDeviceIsAnInvalidSignature() throws Exception {
    Sensor known = sensor("telemetry.unknown");
    Sensor ghost =
        new Sensor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DeviceRequestSigner.newKeyPair(),
            known.session());

    signed(ghost, batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
  }

  @Test
  void aLevelOutOfTheTankRangeIsRefusedAndNothingIsStored() throws Exception {
    Sensor sensor = sensor("telemetry.range");
    UUID refused = UUID.randomUUID();
    UUID battery = UUID.randomUUID();

    signed(sensor, batch(refused, battery, OUT_OF_RANGE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(1))
        .andExpect(jsonPath("$.rejected").value(1))
        .andExpect(jsonPath("$.results[0].status").value("REJECTED"))
        .andExpect(jsonPath("$.results[0].error.code").value("GAUGE_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.results[1].status").value("ACCEPTED"));

    assertThat(count("SELECT count(*) FROM ops.readings WHERE id = ?", refused)).isZero();
    assertThat(count("SELECT count(*) FROM devices.telemetry_points WHERE id = ?", refused))
        .isZero();
  }

  @Test
  void aBodyOver256KiBIsTooLargeBeforeTheSignatureIsChecked() throws Exception {
    Sensor sensor = sensor("telemetry.large");
    byte[] body = new byte[OVER_LIMIT];
    Arrays.fill(body, (byte) ' ');

    mvc.perform(
            post(PATH)
                .header("X-Device-Id", sensor.device().toString())
                .header("X-Timestamp", Long.toString(clock.instant().getEpochSecond()))
                .header("X-Nonce", nonce())
                .header("X-Signature", "A".repeat(86))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
  }

  @Test
  void aSignedBatchRefusedWithBadBodyKeepsItsNonceAgainstReplay() throws Exception {
    Sensor sensor = sensor("telemetry.badbody");
    long now = clock.instant().getEpochSecond();
    String nonce = nonce();
    byte[] refused = bytes("{\"points\":\"no es una lista\"}");
    signedPost(sensor, now, nonce, refused, refused).andExpect(status().isBadRequest());

    byte[] valid = bytes(batch(UUID.randomUUID(), UUID.randomUUID(), "2.10"));
    signedPost(sensor, now, nonce, valid, valid)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("NONCE_REPLAY"));
  }
}
