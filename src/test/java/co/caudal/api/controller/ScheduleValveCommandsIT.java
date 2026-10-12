package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.DeviceRequestSigner;
import co.caudal.support.PublishedScheduleIT;
import java.security.KeyPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The valve commands that a published schedule generates (docs/Protocolo-de-dispositivos.md,
 * section 5.1): OPEN from two minutes before the shift, CLOSE from its end, both valid ten minutes.
 */
class ScheduleValveCommandsIT extends PublishedScheduleIT {

  private static final String ALTO = "Sector ALTO";
  private static final String BAJO = "Sector BAJO";

  /** The first valve of the sector, by code: the one the tests make motorized. */
  private UUID firstValveOf(UUID aqueduct, String sector) {
    return owner.queryForObject(
        "SELECT v.id FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
            + " WHERE s.aqueduct_id = ? AND s.name = ? ORDER BY v.code LIMIT 1",
        UUID.class,
        aqueduct,
        sector);
  }

  /** The schedule item of a sector in a proposal. */
  private UUID itemOf(String proposal, String sector) {
    return owner.queryForObject(
        "SELECT si.id FROM ops.schedule_items si JOIN org.sectors s ON s.id = si.sector_id"
            + " WHERE si.proposal_id = ?::uuid AND s.name = ?",
        UUID.class,
        proposal,
        sector);
  }

  private long count(String sql, Object... args) {
    return owner.queryForObject(sql, Long.class, args);
  }

  /** Creates an active VALVE_ACTUATOR for the valve, as the board does, and returns its id. */
  private UUID actuator(Session admin, UUID valve) throws Exception {
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
    String text = created.andReturn().getResponse().getContentAsString();
    UUID device = UUID.fromString(text.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1"));
    mvc.perform(
            post("/api/v1/devices/" + device + "/keys")
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"public_key\":\"" + DeviceRequestSigner.publicKeyText(pair) + "\"}"))
        .andExpect(status().isCreated());
    return device;
  }

  @Test
  void publishingCreatesOpenAndCloseCommandsOnlyForMotorizedValvesWithAnActuator()
      throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("valves.member", "BOARD_MEMBER", aqueduct));
    Session admin = login(account("valves.admin", "BOARD_ADMIN", aqueduct));
    UUID alto = firstValveOf(aqueduct, ALTO);
    UUID bajo = firstValveOf(aqueduct, BAJO);
    owner.update("UPDATE org.valves SET is_motorized = true WHERE id = ?", alto);
    owner.update("UPDATE org.valves SET is_motorized = true WHERE id = ?", bajo);
    actuator(admin, alto);
    String proposal = approvedProposal(member);

    decide(member, proposal, "publish", "{\"lock_version\":1}").andExpect(status().isCreated());

    UUID altoItem = itemOf(proposal, ALTO);
    UUID bajoItem = itemOf(proposal, BAJO);
    assertThat(count("SELECT count(*) FROM devices.valve_commands WHERE valve_id = ?", alto))
        .isEqualTo(2L);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM devices.valve_commands WHERE schedule_item_id = ?",
                Long.class,
                altoItem))
        .isEqualTo(2L);
    assertThat(
            owner.queryForObject(
                "SELECT vc.not_before = si.start_at - interval '2 minutes'"
                    + " AND vc.expires_at = si.start_at + interval '10 minutes'"
                    + " AND vc.created_by IS NULL AND vc.manual_reason IS NULL"
                    + " AND vc.status = 'PENDING'"
                    + " FROM devices.valve_commands vc JOIN ops.schedule_items si"
                    + " ON si.id = vc.schedule_item_id WHERE vc.valve_id = ? AND vc.command = 'OPEN'",
                Boolean.class,
                alto))
        .isTrue();
    assertThat(
            owner.queryForObject(
                "SELECT vc.not_before = si.end_at"
                    + " AND vc.expires_at = si.end_at + interval '10 minutes'"
                    + " AND vc.created_by IS NULL AND vc.status = 'PENDING'"
                    + " FROM devices.valve_commands vc JOIN ops.schedule_items si"
                    + " ON si.id = vc.schedule_item_id WHERE vc.valve_id = ? AND vc.command = 'CLOSE'",
                Boolean.class,
                alto))
        .isTrue();
    assertThat(
            count(
                "SELECT count(*) FROM devices.valve_commands WHERE schedule_item_id = ?", bajoItem))
        .isZero();
  }

  @Test
  void aPendingCommandOfAnotherProposalOfTheDayIsCancelledWithAnEvent() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("valves.replaced", "BOARD_MEMBER", aqueduct));
    Session admin = login(account("valves.replacer", "BOARD_ADMIN", aqueduct));
    UUID alto = firstValveOf(aqueduct, ALTO);
    owner.update("UPDATE org.valves SET is_motorized = true WHERE id = ?", alto);
    UUID device = actuator(admin, alto);

    String first = proposalOf(member);
    decide(member, first, "reject", "{\"lock_version\":0,\"reason\":\"Propuesta de prueba.\"}")
        .andExpect(status().is2xxSuccessful());
    UUID stale =
        owner.queryForObject(
            "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
                + " schedule_item_id, not_before, expires_at) VALUES (?, ?, ?, 'OPEN', ?,"
                + " now() + interval '1 hour', now() + interval '2 hours') RETURNING id",
            UUID.class,
            aqueduct,
            alto,
            device,
            itemOf(first, ALTO));

    String second = approvedProposal(member);
    decide(member, second, "publish", "{\"lock_version\":1}").andExpect(status().isCreated());

    assertThat(
            owner.queryForObject(
                "SELECT status FROM devices.valve_commands WHERE id = ?", String.class, stale))
        .isEqualTo("CANCELLED");
    assertThat(
            count(
                "SELECT count(*) FROM devices.valve_command_events WHERE command_id = ?"
                    + " AND event = 'CANCELLED'",
                stale))
        .isEqualTo(1L);
  }

  /** Generates the proposal of tomorrow without approving it; returns its id. */
  private String proposalOf(Session member) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/schedule-proposals")
                    .header("Authorization", member.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"service_date\":\"" + TOMORROW + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }
}
