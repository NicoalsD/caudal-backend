package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Proposals of shifts end to end: generation, review and decisions of the Board. */
class ScheduleProposalIT extends SessionIT {

  static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);

  UUID tankOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT id FROM org.tanks WHERE aqueduct_id = ? ORDER BY name LIMIT 1",
        UUID.class,
        aqueduct);
  }

  UUID ruleSetOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
        UUID.class,
        aqueduct);
  }

  UUID sectorOf(UUID aqueduct, String code) {
    return owner.queryForObject(
        "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = ?",
        UUID.class,
        aqueduct,
        code);
  }

  void reading(UUID aqueduct, String value) {
    Instant at = Instant.now().minus(1, ChronoUnit.HOURS);
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?,"
            + " 'ACCEPTED')",
        aqueduct,
        tankOf(aqueduct),
        value,
        Timestamp.from(at),
        Timestamp.from(at),
        ruleSetOf(aqueduct));
  }

  ResultActions generate(Session session, LocalDate day) throws Exception {
    return mvc.perform(
        post("/api/v1/schedule-proposals")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"service_date\":\"" + day + "\"}"));
  }

  @Test
  void aLowTankGivesExplainedShiftsPendingReviewAndOnlyOncePerDay() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.low", "BOARD_MEMBER", aqueduct));

    generate(member, TOMORROW)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.tank_band").value("LOW"))
        .andExpect(jsonPath("$.available_hours").value(8.00))
        .andExpect(jsonPath("$.strategy_code").value("PRIORITY_THEN_LONGEST_WAIT"))
        .andExpect(jsonPath("$.lock_version").value(0))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sector_id").value(sectorOf(aqueduct, "ALTO").toString()))
        .andExpect(jsonPath("$.items[0].sequence").value(1))
        .andExpect(jsonPath("$.items[0].origin").value("PROPOSED"))
        .andExpect(jsonPath("$.items[0].reasons[0].code").value("PRIORITY_SECTOR"))
        .andExpect(jsonPath("$.items[0].reasons[0].text").isNotEmpty())
        .andExpect(jsonPath("$.items[1].sector_id").value(sectorOf(aqueduct, "BAJO").toString()))
        .andExpect(jsonPath("$.decisions.length()").value(0))
        .andExpect(jsonPath("$.snapshot_taken").value(false));

    generate(member, TOMORROW)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void aCriticalTankServesOnlyPrioritySectors() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "1.00");
    Session member = login(account("proposal.critical", "BOARD_MEMBER", aqueduct));

    generate(member, TOMORROW)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tank_band").value("CRITICAL"))
        .andExpect(jsonPath("$.available_hours").value(3.00))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(
            jsonPath("$.unserved_sectors[0].sector_id")
                .value(sectorOf(aqueduct, "BAJO").toString()))
        .andExpect(jsonPath("$.unserved_sectors[0].reason").value("PRIORITY_ONLY"));
  }

  @Test
  void theLastPublishedShiftCountsAsHoursWithoutService() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    UUID past =
        owner.queryForObject(
            "INSERT INTO ops.schedule_proposals (aqueduct_id, service_date, rule_set_id, status,"
                + " tank_level_snapshot, tank_band, available_hours, strategy_code) VALUES (?,"
                + " ?, ?, 'PUBLISHED', 2.10, 'LOW', 8.00, 'PRIORITY_THEN_LONGEST_WAIT')"
                + " RETURNING id",
            UUID.class,
            aqueduct,
            TOMORROW.minusDays(2),
            ruleSetOf(aqueduct));
    Instant end = Instant.now().minus(1, ChronoUnit.DAYS);
    owner.update(
        "INSERT INTO ops.schedule_items (aqueduct_id, proposal_id, sector_id, sequence, start_at,"
            + " end_at) VALUES (?, ?, ?, 1, ?, ?)",
        aqueduct,
        past,
        sectorOf(aqueduct, "BAJO"),
        Timestamp.from(end.minus(2, ChronoUnit.HOURS)),
        Timestamp.from(end));
    Session member = login(account("proposal.history", "BOARD_MEMBER", aqueduct));

    generate(member, TOMORROW)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[1].reasons[0].code").value("HOURS_WITHOUT_SERVICE"))
        .andExpect(jsonPath("$.items[1].reasons[0].params.origen").value("ESTIMATED"));
  }

  @Test
  void withoutLevelOrPermissionItIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("proposal.nolevel", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("proposal.operator", "OPERATOR", aqueduct));

    generate(member, TOMORROW)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("NO_LEVEL_DATA"));
    generate(operator, TOMORROW).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/schedule-proposals")
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"service_date\":\"mañana\"}"))
        .andExpect(status().isBadRequest());
  }
}
