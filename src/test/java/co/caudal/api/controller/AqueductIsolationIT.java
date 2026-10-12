package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Tenant isolation across two aqueducts, end to end. A user of aqueduct A never sees or changes the
 * rows of aqueduct B (404 or an empty list), and a device of A never sees or confirms the valve
 * commands of B. The per resource tests of each controller cover the rest of the matrix.
 */
class AqueductIsolationIT extends SessionIT {

  /** Seconds of margin for the moments of the commands, so the test never races the clock. */
  private static final long MARGIN = 600;

  private static final String DEVICE_PATH = "/api/v1/devices/commands";

  @Autowired private Clock clock;

  /** An aqueduct with its board administrator, its tank, its sector and its valves. */
  private record Tenant(Session session, UUID tank, UUID sector, List<UUID> valves) {}

  /** A valve actuator with its key pair and the valve it moves. */
  private record Actuator(UUID device, KeyPair pair, UUID valve) {}

  private Tenant tenant(String username) throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account(username, "BOARD_ADMIN", aqueduct));
    UUID tank =
        owner.queryForObject(
            "SELECT id FROM org.tanks WHERE aqueduct_id = ?", UUID.class, aqueduct);
    UUID sector =
        owner.queryForObject(
            "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = 'ALTO'",
            UUID.class,
            aqueduct);
    List<UUID> valves =
        owner.queryForList(
            "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
                + " WHERE s.aqueduct_id = ? ORDER BY v.code",
            UUID.class,
            aqueduct);
    return new Tenant(session, tank, sector, valves);
  }

  /** Creates an actuator through the API, as the board of its aqueduct, and registers its key. */
  private Actuator actuator(Tenant on, UUID valve) throws Exception {
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    ResultActions created =
        mvc.perform(
                post("/api/v1/devices")
                    .header("Authorization", on.session().bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"kind\":\"VALVE_ACTUATOR\",\"name\":\"Actuador de prueba\",\"valve_id\":\""
                            + valve
                            + "\"}"))
            .andExpect(status().isCreated());
    UUID device = UUID.fromString(idOf(created));
    registerKey(on, device, pair);
    return new Actuator(device, pair, valve);
  }

  private void registerKey(Tenant on, UUID device, KeyPair pair) throws Exception {
    mvc.perform(
            post("/api/v1/devices/" + device + "/keys")
                .header("Authorization", on.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}"))
        .andExpect(status().isCreated());
  }

  private static String idOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private static String nonce() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /** Inserts a command for the actuator as the owner, which is how nothing else creates them. */
  private UUID command(Actuator actuator, Tenant on, Instant from, Instant to) {
    return owner.queryForObject(
        "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
            + " manual_reason, not_before, expires_at) VALUES (?, ?, ?, 'OPEN', ?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        aqueductOf(on),
        actuator.valve(),
        actuator.device(),
        "Prueba de aislamiento entre acueductos.",
        Timestamp.from(from),
        Timestamp.from(to));
  }

  private UUID aqueductOf(Tenant on) {
    return owner.queryForObject(
        "SELECT aqueduct_id FROM org.tanks WHERE id = ?", UUID.class, on.tank());
  }

  private String statusOf(UUID command) {
    return owner.queryForObject(
        "SELECT status FROM devices.valve_commands WHERE id = ?", String.class, command);
  }

  /** A signed GET of the device: the signature covers the hash of the empty body. */
  private ResultActions signedGet(Actuator actuator) throws Exception {
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    String nonce = nonce();
    String signature =
        DeviceRequestSigner.sign(
            actuator.pair(), "GET", DEVICE_PATH, timestamp, nonce, new byte[0]);
    return mvc.perform(
        get(DEVICE_PATH)
            .header("X-Device-Id", actuator.device().toString())
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature));
  }

  /** A signed POST of a confirmation of the command, as the device would send it. */
  private ResultActions signedAck(Actuator actuator, UUID command, String body) throws Exception {
    String path = DEVICE_PATH + "/" + command + "/ack";
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    String nonce = nonce();
    String signature =
        DeviceRequestSigner.sign(actuator.pair(), "POST", path, timestamp, nonce, bytes);
    return mvc.perform(
        post(path)
            .header("X-Device-Id", actuator.device().toString())
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content(bytes));
  }

  @Test
  void aUserOfAnotherAqueductCannotReadOrChangeTheTanksDevicesAndValves() throws Exception {
    Tenant mine = tenant("isolation.read.mine");
    Tenant theirs = tenant("isolation.read.theirs");
    Actuator device = actuator(theirs, theirs.valves().get(0));
    KeyPair foreignKey = DeviceRequestSigner.newKeyPair();
    UUID otherValve = theirs.valves().get(1);

    mvc.perform(
            get("/api/v1/tanks/" + theirs.tank() + "/status")
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mvc.perform(
            get("/api/v1/tanks/" + theirs.tank() + "/forecasts/latest")
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/readings?tank_id=" + theirs.tank())
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/incidents?sector_id=" + theirs.sector())
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());

    mvc.perform(
            patch("/api/v1/valves/" + otherValve)
                .header("Authorization", mine.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"is_motorized\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/devices/" + device.device())
                .header("Authorization", mine.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/devices/" + device.device() + "/keys")
                .header("Authorization", mine.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(foreignKey) + "\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete("/api/v1/devices/" + device.device() + "/keys/" + UUID.randomUUID())
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isNotFound());

    assertThat(
            owner.queryForObject(
                "SELECT is_motorized FROM org.valves WHERE id = ?", Boolean.class, otherValve))
        .isFalse();
    assertThat(
            owner.queryForObject(
                "SELECT status FROM devices.devices WHERE id = ?", String.class, device.device()))
        .isEqualTo("ACTIVE");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.device_keys WHERE device_id = ?",
                Integer.class,
                device.device()))
        .isEqualTo(1);
  }

  @Test
  void theAuditLogAndTheSecurityEventsOfAnotherAqueductAreNeverListed() throws Exception {
    Tenant mine = tenant("isolation.audit.mine");
    Tenant theirs = tenant("isolation.audit.theirs");
    actuator(theirs, theirs.valves().get(0));
    owner.update(
        "INSERT INTO iam.security_events (aqueduct_id, type, severity, actor_user_id, ip_hmac,"
            + " request_id, details, created_at) VALUES (?, 'RATE_LIMITED', 'LOW', NULL, ?, ?,"
            + " '{}'::jsonb, ?)",
        aqueductOf(theirs),
        "b".repeat(64),
        "req-isolation-theirs",
        OffsetDateTime.now(ZoneOffset.UTC));

    mvc.perform(get("/api/v1/security-events").header("Authorization", mine.session().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.request_id == 'req-isolation-theirs')]").isEmpty());
    mvc.perform(get("/api/v1/security-events").header("Authorization", theirs.session().bearer()))
        .andExpect(jsonPath("$.items[?(@.request_id == 'req-isolation-theirs')]").isNotEmpty());

    mvc.perform(
            get("/api/v1/audit-log?actor_id=" + theirs.session().userId())
                .header("Authorization", mine.session().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(
            get("/api/v1/audit-log?actor_id=" + theirs.session().userId())
                .header("Authorization", theirs.session().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isNotEmpty());
  }

  @Test
  void aDeviceOfAnotherAqueductSeesNoCommandsOfTheTenantAndCannotConfirmThem() throws Exception {
    Tenant mine = tenant("isolation.device.mine");
    Tenant theirs = tenant("isolation.device.theirs");
    Actuator mineDevice = actuator(mine, mine.valves().get(0));
    Actuator theirDevice = actuator(theirs, theirs.valves().get(0));
    Instant now = clock.instant();
    UUID theirCommand = command(theirDevice, theirs, now.minusSeconds(60), now.plusSeconds(MARGIN));

    signedGet(mineDevice).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    signedAck(mineDevice, theirCommand, "{\"result\":\"ACKED\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    assertThat(statusOf(theirCommand)).isEqualTo("PENDING");

    signedGet(theirDevice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(theirCommand.toString()));
  }
}
