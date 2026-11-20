package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The autopilot end to end (HU-40 to HU-42): settings, a forced cycle with the deterministic rules
 * (the test profile has no assistant service), accepting and rejecting, the reserve floor, the
 * automatic mode and its pause by a human action, and the isolation between aqueducts.
 */
class AutopilotIT extends SessionIT {

  private static final String BASE = "/api/v1/autopilot";
  private static final String REASON = "Cierre preventivo por fuga visible en el tubo.";

  @Autowired private Clock clock;

  /** An aqueduct with an administrator, a motorized valve with an actuator and a tank level. */
  private record Site(
      UUID aqueduct,
      Session admin,
      UUID tank,
      UUID valve,
      UUID sector,
      UUID device,
      KeyPair pair) {}

  private Site site(String name, String level) throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account(name, "BOARD_ADMIN", aqueduct));
    UUID tank =
        owner.queryForObject(
            "SELECT id FROM org.tanks WHERE aqueduct_id = ?", UUID.class, aqueduct);
    reading(aqueduct, tank, level);
    List<UUID> valves =
        owner.queryForList(
            "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
                + " WHERE s.aqueduct_id = ? ORDER BY v.code",
            UUID.class,
            aqueduct);
    UUID valve = valves.get(0);
    mvcPatch(admin, "/api/v1/valves/" + valve, "{\"is_motorized\":true}");
    KeyPair pair = DeviceRequestSigner.newKeyPair();
    ResultActions created =
        mvc.perform(
                post("/api/v1/devices")
                    .header("Authorization", admin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"kind\":\"VALVE_ACTUATOR\",\"name\":\"Actuador de prueba\",\"valve_id\":\""
                            + valve
                            + "\"}"))
            .andExpect(status().isCreated());
    UUID device = UUID.fromString(idOf(created));
    mvc.perform(
            post("/api/v1/devices/" + device + "/keys")
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}"))
        .andExpect(status().isCreated());
    UUID sector =
        owner.queryForObject("SELECT sector_id FROM org.valves WHERE id = ?", UUID.class, valve);
    return new Site(aqueduct, admin, tank, valve, sector, device, pair);
  }

  private void mvcPatch(Session session, String path, String body) throws Exception {
    mvc.perform(
            patch(path)
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private static String idOf(ResultActions result) throws Exception {
    return result
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private void reading(UUID aqueduct, UUID tank, String value) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    Timestamp now = Timestamp.from(clock.instant());
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?,"
            + " 'ACCEPTED')",
        aqueduct,
        tank,
        value,
        now,
        now,
        ruleSet);
  }

  /** Leaves the valve open, as a confirmed OPEN command would. */
  private void valveIsOpen(Site on) {
    UUID command =
        owner.queryForObject(
            "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
                + " manual_reason, status, not_before, expires_at, created_at) VALUES"
                + " (?, ?, ?, 'OPEN', 'Apertura confirmada de prueba.', 'ACKED', ?, ?, ?)"
                + " RETURNING id",
            UUID.class,
            on.aqueduct(),
            on.valve(),
            on.device(),
            Timestamp.from(clock.instant().minusSeconds(7200)),
            Timestamp.from(clock.instant().minusSeconds(6600)),
            Timestamp.from(clock.instant().minusSeconds(7200)));
    owner.update(
        "INSERT INTO devices.valve_command_events (aqueduct_id, command_id, event, occurred_at)"
            + " VALUES (?, ?, 'ACKED', ?)",
        on.aqueduct(),
        command,
        Timestamp.from(clock.instant().minusSeconds(7000)));
  }

  private ResultActions run(Session session) throws Exception {
    return mvc.perform(post(BASE + "/run").header("Authorization", session.bearer()));
  }

  private void settings(Session session, String mode, int minutes, String floor) throws Exception {
    mvc.perform(
            put(BASE + "/settings")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"mode\":\""
                        + mode
                        + "\",\"operator_absence_minutes\":"
                        + minutes
                        + ",\"reserve_floor_pct\":"
                        + floor
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value(mode));
  }

  private static String decisionId(ResultActions run) throws Exception {
    return run.andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private ResultActions signed(Site on, String method, String path, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String timestamp = Long.toString(clock.instant().getEpochSecond());
    byte[] nonceBytes = new byte[16];
    new SecureRandom().nextBytes(nonceBytes);
    String nonce = HexFormat.of().formatHex(nonceBytes);
    String signature = DeviceRequestSigner.sign(on.pair(), method, path, timestamp, nonce, bytes);
    var builder = "GET".equals(method) ? get(path) : post(path);
    return mvc.perform(
        builder
            .header("X-Device-Id", on.device().toString())
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content(bytes));
  }

  @Test
  void settingsHaveDefaultsAndOnlyTheAdministratorChangesThem() throws Exception {
    Site on = site("autopilot.settings", "4.00");
    Session operator = login(account("autopilot.settings.op", "OPERATOR", on.aqueduct()));

    mvc.perform(get(BASE + "/settings").header("Authorization", operator.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("ASSISTED"))
        .andExpect(jsonPath("$.operator_absence_minutes").value(30))
        .andExpect(jsonPath("$.reserve_floor_pct").value(25.0));

    settings(on.admin(), "AUTOMATIC", 45, "30");
    mvc.perform(get(BASE + "/settings").header("Authorization", operator.bearer()))
        .andExpect(jsonPath("$.mode").value("AUTOMATIC"))
        .andExpect(jsonPath("$.operator_absence_minutes").value(45));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE aqueduct_id = ?"
                    + " AND action = 'AUTOPILOT_SETTINGS_CHANGED' AND actor_id = ?",
                Integer.class,
                on.aqueduct(),
                on.admin().userId()))
        .isEqualTo(1);

    mvc.perform(
            put(BASE + "/settings")
                .header("Authorization", operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"mode\":\"MANUAL\",\"operator_absence_minutes\":5,\"reserve_floor_pct\":10}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    for (String bad :
        List.of(
            "{\"mode\":\"FREE\",\"operator_absence_minutes\":5,\"reserve_floor_pct\":10}",
            "{\"mode\":\"MANUAL\",\"operator_absence_minutes\":0,\"reserve_floor_pct\":10}",
            "{\"mode\":\"MANUAL\",\"operator_absence_minutes\":1441,\"reserve_floor_pct\":10}",
            "{\"mode\":\"MANUAL\",\"operator_absence_minutes\":5,\"reserve_floor_pct\":101}",
            "{\"mode\":\"MANUAL\",\"operator_absence_minutes\":5}")) {
      mvc.perform(
              put(BASE + "/settings")
                  .header("Authorization", on.admin().bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void aCycleStoresRuleDecisionsAndAPersonAcceptsOneIntoACommand() throws Exception {
    Site on = site("autopilot.accept", "4.00");
    valveIsOpen(on);
    Session operator = login(account("autopilot.accept.op", "OPERATOR", on.aqueduct()));

    String decision =
        decisionId(
            run(on.admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("CLOSE"))
                .andExpect(jsonPath("$.items[0].valve_id").value(on.valve().toString()))
                .andExpect(jsonPath("$.items[0].source").value("RULES"))
                .andExpect(jsonPath("$.items[0].model").value("rules-v1"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING")));

    mvc.perform(get(BASE + "/decisions?status=PENDING").header("Authorization", operator.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(decision));

    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", operator.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.decided_by").value(operator.userId().toString()))
        .andExpect(jsonPath("$.valve_command_id").isNotEmpty());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE autopilot_decision_id = ?"
                    + " AND created_by = ? AND command = 'CLOSE' AND manual_reason IS NULL"
                    + " AND schedule_item_id IS NULL",
                Integer.class,
                UUID.fromString(decision),
                operator.userId()))
        .isEqualTo(1);
    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", operator.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    mvc.perform(get("/api/v1/valves/live").header("Authorization", operator.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.valve_id=='" + on.valve() + "')].state").value("OPEN"))
        .andExpect(
            jsonPath("$.items[?(@.valve_id=='" + on.valve() + "')].last_command.origin")
                .value("AUTOPILOT"))
        .andExpect(
            jsonPath("$.items[?(@.valve_id=='" + on.valve() + "')].last_command.status")
                .value("PENDING"));

    // The actuator receives the command and confirms it: the accepted decision is executed.
    signed(on, "GET", "/api/v1/devices/commands", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].command").value("CLOSE"));
    UUID command =
        owner.queryForObject(
            "SELECT id FROM devices.valve_commands WHERE autopilot_decision_id = ?",
            UUID.class,
            UUID.fromString(decision));
    signed(on, "POST", "/api/v1/devices/commands/" + command + "/ack", "{\"result\":\"ACKED\"}")
        .andExpect(status().isOk());
    assertThat(
            owner.queryForObject(
                "SELECT status FROM ops.autopilot_decisions WHERE id = ?",
                String.class,
                UUID.fromString(decision)))
        .isEqualTo("EXECUTED");
    mvc.perform(get("/api/v1/valves/live").header("Authorization", operator.bearer()))
        .andExpect(jsonPath("$.items[?(@.valve_id=='" + on.valve() + "')].state").value("CLOSED"));
  }

  @Test
  void rejectingKeepsTheReasonAndCreatesNoCommand() throws Exception {
    Site on = site("autopilot.reject", "4.00");
    valveIsOpen(on);
    String decision = decisionId(run(on.admin()).andExpect(status().isOk()));

    mvc.perform(
            post(BASE + "/decisions/" + decision + "/reject")
                .header("Authorization", on.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"El fontanero ya está en el sitio.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.status_detail").value("El fontanero ya está en el sitio."))
        .andExpect(jsonPath("$.valve_command_id").doesNotExist());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE autopilot_decision_id = ?",
                Integer.class,
                UUID.fromString(decision)))
        .isZero();
    mvc.perform(
            post(BASE + "/decisions/" + decision + "/reject")
                .header("Authorization", on.admin().bearer()))
        .andExpect(status().isConflict());
  }

  @Test
  void aNewCycleExpiresThePendingDecisionsOfTheLastOne() throws Exception {
    Site on = site("autopilot.expire", "4.00");
    valveIsOpen(on);
    String first = decisionId(run(on.admin()).andExpect(status().isOk()));
    String second = decisionId(run(on.admin()).andExpect(status().isOk()));

    assertThat(first).isNotEqualTo(second);
    assertThat(statusOf(first)).isEqualTo("EXPIRED");
    assertThat(statusOf(second)).isEqualTo("PENDING");
    mvc.perform(get(BASE + "/recommendations").header("Authorization", on.admin().bearer()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(second));
  }

  @Test
  void manualModeOnlyShowsTheDecisions() throws Exception {
    Site on = site("autopilot.manual", "4.00");
    valveIsOpen(on);
    settings(on.admin(), "MANUAL", 30, "25");
    String decision = decisionId(run(on.admin()).andExpect(status().isOk()));

    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", on.admin().bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    assertThat(statusOf(decision)).isEqualTo("PENDING");
  }

  @Test
  void anOpenAtTheReserveFloorIsSkippedAndNeverBecomesACommand() throws Exception {
    Site on = site("autopilot.floor", "1.00");
    UUID decision =
        owner.queryForObject(
            "INSERT INTO ops.autopilot_decisions (aqueduct_id, source, model, action, valve_id,"
                + " reason, confidence) VALUES (?, 'LLM', 'modelo-de-prueba', 'OPEN', ?,"
                + " 'Abrir para llenar el sector.', 0.9) RETURNING id",
            UUID.class,
            on.aqueduct(),
            on.valve());

    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", on.admin().bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    assertThat(statusOf(decision.toString())).isEqualTo("SKIPPED");
    assertThat(
            owner.queryForObject(
                "SELECT status_detail FROM ops.autopilot_decisions WHERE id = ?",
                String.class,
                decision))
        .isEqualTo("RESERVE_FLOOR");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE autopilot_decision_id = ?",
                Integer.class,
                decision))
        .isZero();
  }

  @Test
  void automaticModeExecutesByItselfAndLeavesTheAuditTrail() throws Exception {
    Site on = site("autopilot.auto", "4.00");
    valveIsOpen(on);
    settings(on.admin(), "AUTOMATIC", 30, "25");

    run(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("CLOSE"))
        .andExpect(jsonPath("$.items[0].status").value("EXECUTED"))
        .andExpect(jsonPath("$.items[0].decided_by").doesNotExist())
        .andExpect(jsonPath("$.items[0].valve_command_id").isNotEmpty());
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE aqueduct_id = ?"
                    + " AND autopilot_decision_id IS NOT NULL AND created_by IS NULL",
                Integer.class,
                on.aqueduct()))
        .isEqualTo(1);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE aqueduct_id = ?"
                    + " AND action = 'AUTOPILOT_EXECUTED' AND actor_type = 'SYSTEM'",
                Integer.class,
                on.aqueduct()))
        .isEqualTo(1);
  }

  @Test
  void aHumanActionPausesTheAutomaticMode() throws Exception {
    Site on = site("autopilot.pause", "4.00");
    valveIsOpen(on);
    settings(on.admin(), "AUTOMATIC", 30, "25");
    Instant later = clock.instant();
    mvc.perform(
            post("/api/v1/valve-commands/manual")
                .header("Authorization", on.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"valve_id\":\""
                        + on.valve()
                        + "\",\"command\":\"OPEN\",\"manual_reason\":\""
                        + REASON
                        + "\",\"not_before\":\""
                        + later.plusSeconds(3600)
                        + "\",\"expires_at\":\""
                        + later.plusSeconds(4200)
                        + "\"}"))
        .andExpect(status().isCreated());

    run(on.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("CLOSE"))
        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.items[0].status_detail").value("OPERATOR_ACTIVE"));
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE aqueduct_id = ?"
                    + " AND autopilot_decision_id IS NOT NULL",
                Integer.class,
                on.aqueduct()))
        .isZero();
  }

  @Test
  void decisionsAreInvisibleToAnotherAqueduct() throws Exception {
    Site mine = site("autopilot.iso.mine", "4.00");
    Site theirs = site("autopilot.iso.theirs", "4.00");
    valveIsOpen(theirs);
    String decision = decisionId(run(theirs.admin()).andExpect(status().isOk()));

    mvc.perform(get(BASE + "/decisions").header("Authorization", mine.admin().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", mine.admin().bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(BASE + "/decisions/" + decision + "/reject")
                .header("Authorization", mine.admin().bearer()))
        .andExpect(status().isNotFound());
    assertThat(statusOf(decision)).isEqualTo("PENDING");
  }

  @Test
  void permissionsFollowTheRoles() throws Exception {
    Site on = site("autopilot.roles", "4.00");
    valveIsOpen(on);
    String decision = decisionId(run(on.admin()).andExpect(status().isOk()));
    Session member = login(account("autopilot.roles.member", "BOARD_MEMBER", on.aqueduct()));
    Session operator = login(account("autopilot.roles.op", "OPERATOR", on.aqueduct()));
    Session support = login(account("autopilot.roles.support", "SUPPORT_ENTITY", on.aqueduct()));

    mvc.perform(get(BASE + "/decisions").header("Authorization", member.bearer()))
        .andExpect(status().isOk());
    mvc.perform(
            post(BASE + "/decisions/" + decision + "/accept")
                .header("Authorization", member.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(post(BASE + "/run").header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/decisions").header("Authorization", support.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/settings").header("Authorization", support.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/valves/live").header("Authorization", support.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/decisions?status=BOGUS").header("Authorization", operator.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(BASE + "/decisions?limit=0").header("Authorization", operator.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aCycleWithoutMotorizedValvesDecidesNothing() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session admin = login(account("autopilot.empty", "BOARD_ADMIN", aqueduct));

    run(admin).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  }

  private String statusOf(String decision) {
    return owner.queryForObject(
        "SELECT status FROM ops.autopilot_decisions WHERE id = ?",
        String.class,
        UUID.fromString(decision));
  }

  @Test
  void aCommandHasExactlyOneOrigin() throws Exception {
    Site on = site("autopilot.origin", "4.00");
    UUID decision =
        owner.queryForObject(
            "INSERT INTO ops.autopilot_decisions (aqueduct_id, source, model, action, valve_id,"
                + " reason, confidence) VALUES (?, 'RULES', 'rules-v1', 'CLOSE', ?,"
                + " 'Cerrar la llave del sector.', 0.7) RETURNING id",
            UUID.class,
            on.aqueduct(),
            on.valve());
    Timestamp from = Timestamp.from(clock.instant());
    Timestamp to = Timestamp.from(clock.instant().plusSeconds(600));
    String insert =
        "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
            + " manual_reason, autopilot_decision_id, not_before, expires_at)"
            + " VALUES (?, ?, ?, 'CLOSE', ?, ?, ?, ?)";

    owner.update(insert, on.aqueduct(), on.valve(), on.device(), null, decision, from, to);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                owner.update(
                    insert,
                    on.aqueduct(),
                    on.valve(),
                    on.device(),
                    REASON,
                    decision,
                    Timestamp.from(clock.instant().plusSeconds(1200)),
                    Timestamp.from(clock.instant().plusSeconds(1800))))
        .hasStackTraceContaining("valve_commands_origin_required");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                owner.update(
                    insert,
                    on.aqueduct(),
                    on.valve(),
                    on.device(),
                    null,
                    null,
                    Timestamp.from(clock.instant().plusSeconds(2400)),
                    Timestamp.from(clock.instant().plusSeconds(3000))))
        .hasStackTraceContaining("valve_commands_origin_required");
  }
}
