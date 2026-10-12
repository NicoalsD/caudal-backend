package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.SessionIT;
import java.security.KeyPair;
import java.security.SecureRandom;
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
 * Manual valve commands end to end (docs/API.md, section 16.6; docs/Protocolo-de-dispositivos.md,
 * sections 5.1 and 5.5): the board creates them with a reason, and the actuator receives them in
 * its signed GET.
 */
class ManualValveCommandIT extends SessionIT {

  private static final String MANUAL = "/api/v1/valve-commands/manual";
  private static final String PATH = "/api/v1/devices/commands";
  private static final String REASON = "Fuga visible en el tubo; cierre preventivo.";

  @Autowired private Clock clock;

  /** An actuator with its key and the valve it moves. */
  private record Actuator(UUID device, KeyPair pair, UUID valve) {}

  /** An aqueduct with its board administrator and the ids of its valves, ordered by code. */
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

  /** Makes a valve motorized, as the board does with PATCH /api/v1/valves/{id}. */
  private void motorize(Aqueduct on, UUID valve) throws Exception {
    mvc.perform(
            patch("/api/v1/valves/" + valve)
                .header("Authorization", on.session().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"is_motorized\":true}"))
        .andExpect(status().isOk());
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
    new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /** A manual command body; the moments are relative to now. */
  private String body(UUID valve, String command, String reason, long fromSeconds, long toSeconds) {
    Instant now = clock.instant();
    return "{\"valve_id\":\""
        + valve
        + "\",\"command\":\""
        + command
        + "\",\"manual_reason\":"
        + (reason == null ? "null" : "\"" + reason + "\"")
        + ",\"not_before\":\""
        + now.plusSeconds(fromSeconds)
        + "\",\"expires_at\":\""
        + now.plusSeconds(toSeconds)
        + "\"}";
  }

  private ResultActions send(Session session, String body) throws Exception {
    return mvc.perform(
        post(MANUAL)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
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

  private String statusOf(UUID command) {
    return owner.queryForObject(
        "SELECT status FROM devices.valve_commands WHERE id = ?", String.class, command);
  }

  @Test
  void aManualCommandIsCreatedPendingAuditedAndDeliveredToTheActuator() throws Exception {
    Aqueduct on = aqueduct("manual.created");
    UUID valve = on.valves().get(0);
    motorize(on, valve);
    Actuator actuator = actuator(on, valve);

    String id =
        idOf(
            send(on.session(), body(valve, "CLOSE", REASON, -60, 600))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valve_id").value(valve.toString()))
                .andExpect(jsonPath("$.command").value("CLOSE"))
                .andExpect(jsonPath("$.status").value("PENDING")),
            "id");

    UUID command = UUID.fromString(id);
    assertThat(
            owner.queryForObject(
                "SELECT created_by FROM devices.valve_commands WHERE id = ?", UUID.class, command))
        .isEqualTo(on.session().userId());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE entity_type = 'valve_command'"
                    + " AND entity_id = ? AND action = 'CREATE_MANUAL_VALVE' AND actor_id = ?",
                Long.class,
                id,
                on.session().userId()))
        .isEqualTo(1L);

    signedGet(actuator)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].command").value("CLOSE"));
    assertThat(statusOf(command)).isEqualTo("DELIVERED");
  }

  @Test
  void anOperatorCannotSendAManualCommand() throws Exception {
    Aqueduct on = aqueduct("manual.operator");
    UUID valve = on.valves().get(0);
    motorize(on, valve);
    actuator(on, valve);
    Session operator = login(account("manual.op", "OPERATOR", on.aqueduct()));

    send(operator, body(valve, "OPEN", REASON, 60, 600))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void aMissingOrShortReasonIsReasonRequired() throws Exception {
    Aqueduct on = aqueduct("manual.reason");
    UUID valve = on.valves().get(0);
    motorize(on, valve);
    actuator(on, valve);

    send(on.session(), body(valve, "OPEN", "corto", 60, 600))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    send(on.session(), body(valve, "OPEN", null, 60, 600))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
  }

  @Test
  void aNonMotorizedValveIsNotFound() throws Exception {
    Aqueduct on = aqueduct("manual.fixed");
    UUID valve = on.valves().get(0);
    actuator(on, valve);

    send(on.session(), body(valve, "OPEN", REASON, 60, 600))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void aMotorizedValveWithoutAnActiveActuatorIsNotFound() throws Exception {
    Aqueduct on = aqueduct("manual.noactuator");
    UUID valve = on.valves().get(0);
    motorize(on, valve);

    send(on.session(), body(valve, "OPEN", REASON, 60, 600))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void aValveOfAnotherAqueductIsNotFound() throws Exception {
    Aqueduct mine = aqueduct("manual.mine");
    Aqueduct theirs = aqueduct("manual.theirs");
    UUID valve = theirs.valves().get(0);
    motorize(theirs, valve);
    actuator(theirs, valve);

    send(mine.session(), body(valve, "OPEN", REASON, 60, 600))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void anExpiryNotAfterNotBeforeIsAValidationError() throws Exception {
    Aqueduct on = aqueduct("manual.window");
    UUID valve = on.valves().get(0);
    motorize(on, valve);
    actuator(on, valve);

    send(on.session(), body(valve, "OPEN", REASON, 600, 60))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void aSecondActiveCommandWithTheSameNotBeforeIsAConflict() throws Exception {
    Aqueduct on = aqueduct("manual.duplicate");
    UUID valve = on.valves().get(0);
    motorize(on, valve);
    actuator(on, valve);
    String first = body(valve, "OPEN", REASON, 60, 600);

    send(on.session(), first).andExpect(status().isCreated());
    send(on.session(), first)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void aMissingValveIsAValidationError() throws Exception {
    Aqueduct on = aqueduct("manual.missing");

    send(on.session(), "{\"command\":\"OPEN\",\"manual_reason\":\"" + REASON + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }
}
