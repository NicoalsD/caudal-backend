package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Valve commands end to end: real signed requests against the API (docs/API.md, section 16.2 and
 * docs/Protocolo-de-dispositivos.md, section 5.3 and 5.4). The commands are inserted as the owner,
 * because nothing creates them yet.
 */
class ValveCommandDeliveryIT extends SessionIT {

  private static final String PATH = "/api/v1/devices/commands";

  /** Seconds of margin for the moments of the commands, so the test never races the clock. */
  private static final long MARGIN = 600;

  @Autowired private Clock clock;

  /** An actuator with its key, the session of its aqueduct and the valve it moves. */
  private record Actuator(UUID device, KeyPair pair, UUID valve) {}

  /** A test aqueduct with its signed-in administrator and the valves of its two sectors. */
  private record Aqueduct(Session session, UUID aqueduct, List<UUID> valves) {}

  private Aqueduct aqueduct(String username) throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account(username, "BOARD_ADMIN", aqueduct));
    List<UUID> valves =
        owner.queryForList(
            "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
                + " WHERE s.aqueduct_id = ? ORDER BY v.code",
            UUID.class,
            aqueduct);
    return new Aqueduct(session, aqueduct, valves);
  }

  private Actuator actuator(Aqueduct on, UUID valve) throws Exception {
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
    UUID device = UUID.fromString(idOf(created, "id"));
    mvc.perform(
            post("/api/v1/devices/" + device + "/keys")
                .header("Authorization", on.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}"))
        .andExpect(status().isCreated());
    return new Actuator(device, pair, valve);
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

  /** A command inserted as the owner, a manual one with its reason. */
  private UUID command(Actuator actuator, Aqueduct on, String command, Instant from, Instant to) {
    return owner.queryForObject(
        "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
            + " manual_reason, not_before, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        on.aqueduct(),
        actuator.valve(),
        actuator.device(),
        command,
        "Prueba de entrega manual",
        Timestamp.from(from),
        Timestamp.from(to));
  }

  private String statusOf(UUID command) {
    return owner.queryForObject(
        "SELECT status FROM devices.valve_commands WHERE id = ?", String.class, command);
  }

  private long events(UUID command, String event) {
    return owner.queryForObject(
        "SELECT count(*) FROM devices.valve_command_events WHERE command_id = ? AND event = ?",
        Long.class,
        command,
        event);
  }

  /** A signed GET of the actuator: the signature covers the hash of the empty body. */
  private ResultActions signedGet(Actuator actuator) throws Exception {
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    String nonce = nonce();
    String signature =
        DeviceRequestSigner.sign(actuator.pair(), "GET", PATH, timestamp, nonce, new byte[0]);
    return mvc.perform(
        get(PATH)
            .header("X-Device-Id", actuator.device().toString())
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature));
  }

  @Test
  void aDueCommandIsDeliveredAndMarkedDelivered() throws Exception {
    Aqueduct on = aqueduct("valve.due");
    Actuator actuator = actuator(on, on.valves().get(0));
    Instant now = clock.instant();
    UUID command = command(actuator, on, "OPEN", now.minusSeconds(60), now.plusSeconds(MARGIN));

    signedGet(actuator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(command.toString()))
        .andExpect(jsonPath("$.items[0].valve_id").value(actuator.valve().toString()))
        .andExpect(jsonPath("$.items[0].command").value("OPEN"))
        .andExpect(jsonPath("$.items[0].not_before").exists())
        .andExpect(jsonPath("$.items[0].expires_at").exists())
        .andExpect(jsonPath("$.items[1]").doesNotExist());

    assertThat(statusOf(command)).isEqualTo("DELIVERED");
    assertThat(events(command, "DELIVERED")).isEqualTo(1);
  }

  @Test
  void anUnconfirmedCommandIsDeliveredAgainWithoutANewEvent() throws Exception {
    Aqueduct on = aqueduct("valve.again");
    Actuator actuator = actuator(on, on.valves().get(0));
    Instant now = clock.instant();
    UUID command = command(actuator, on, "CLOSE", now.minusSeconds(60), now.plusSeconds(MARGIN));

    signedGet(actuator).andExpect(status().isOk());
    signedGet(actuator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(command.toString()))
        .andExpect(jsonPath("$.items[0].command").value("CLOSE"));

    assertThat(statusOf(command)).isEqualTo("DELIVERED");
    assertThat(events(command, "DELIVERED")).isEqualTo(1);
  }

  @Test
  void theCommandsComeOrderedByNotBefore() throws Exception {
    Aqueduct on = aqueduct("valve.order");
    Actuator actuator = actuator(on, on.valves().get(0));
    Instant now = clock.instant();
    UUID later = command(actuator, on, "CLOSE", now.minusSeconds(30), now.plusSeconds(MARGIN));
    UUID earlier = command(actuator, on, "OPEN", now.minusSeconds(120), now.plusSeconds(MARGIN));

    signedGet(actuator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(earlier.toString()))
        .andExpect(jsonPath("$.items[1].id").value(later.toString()));
  }

  @Test
  void aCommandWithAFutureNotBeforeIsNotReturned() throws Exception {
    Aqueduct on = aqueduct("valve.future");
    Actuator actuator = actuator(on, on.valves().get(0));
    Instant now = clock.instant();
    UUID command =
        command(actuator, on, "OPEN", now.plusSeconds(3600), now.plusSeconds(3600 + MARGIN));

    signedGet(actuator).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

    assertThat(statusOf(command)).isEqualTo("PENDING");
    assertThat(events(command, "DELIVERED")).isZero();
  }

  @Test
  void anExpiredCommandIsNeverDeliveredAndIsMarkedExpired() throws Exception {
    Aqueduct on = aqueduct("valve.expired");
    Actuator actuator = actuator(on, on.valves().get(0));
    Instant now = clock.instant();
    UUID command = command(actuator, on, "OPEN", now.minusSeconds(7200), now.minusSeconds(60));

    signedGet(actuator).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

    assertThat(statusOf(command)).isEqualTo("EXPIRED");
    assertThat(events(command, "EXPIRED")).isEqualTo(1);
    assertThat(events(command, "DELIVERED")).isZero();
  }

  @Test
  void theCommandsOfAnotherDeviceAreNotVisible() throws Exception {
    Aqueduct on = aqueduct("valve.other");
    Actuator mine = actuator(on, on.valves().get(0));
    Actuator other = actuator(on, on.valves().get(1));
    Instant now = clock.instant();
    UUID theirs = command(other, on, "OPEN", now.minusSeconds(60), now.plusSeconds(MARGIN));

    signedGet(mine).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

    assertThat(statusOf(theirs)).isEqualTo("PENDING");
    signedGet(other)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(theirs.toString()));
  }

  @Test
  void aRequestWithoutSignatureIsUnauthorized() throws Exception {
    mvc.perform(get(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
  }
}
