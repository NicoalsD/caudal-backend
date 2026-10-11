package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import com.jayway.jsonpath.JsonPath;
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

  String generatedId(Session session, LocalDate day) throws Exception {
    return JsonPath.read(
        generate(session, day).andReturn().getResponse().getContentAsString(), "$.id");
  }

  ResultActions decide(Session session, String id, String action, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/schedule-proposals/" + id + "/" + action)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
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

  @Test
  void theBoardListsProposalsByDayAndStatusPageByPage() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.list", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("proposal.list.op", "OPERATOR", aqueduct));
    generate(member, TOMORROW).andExpect(status().isCreated());
    generate(member, TOMORROW.plusDays(1)).andExpect(status().isCreated());
    generate(
        login(account("proposal.list.other", "BOARD_MEMBER", newAqueductWithRules())), TOMORROW);

    String first =
        mvc.perform(
                get("/api/v1/schedule-proposals?limit=1").header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].service_date").value(TOMORROW.plusDays(1).toString()))
            .andExpect(jsonPath("$.items[0].items").doesNotExist())
            .andExpect(jsonPath("$.next_cursor").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(first, "$.next_cursor");
    mvc.perform(
            get("/api/v1/schedule-proposals?limit=1&cursor=" + cursor)
                .header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].service_date").value(TOMORROW.toString()))
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
    mvc.perform(
            get("/api/v1/schedule-proposals?status=PENDING_REVIEW&service_date_from="
                    + TOMORROW
                    + "&service_date_to="
                    + TOMORROW)
                .header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            get("/api/v1/schedule-proposals?status=DRAFT").header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/schedule-proposals?service_date_from="
                    + TOMORROW
                    + "&service_date_to="
                    + TOMORROW.minusDays(1))
                .header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/schedule-proposals").header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aProposalIsReadWithItsReasonsAndHiddenFromOtherAqueducts() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.get", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("proposal.get.op", "OPERATOR", aqueduct));
    Session stranger = login(account("proposal.get.other", "BOARD_MEMBER", newAqueductWithRules()));
    String body = generate(member, TOMORROW).andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");

    mvc.perform(get("/api/v1/schedule-proposals/" + id).header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.items[0].reasons[0].text").isNotEmpty())
        .andExpect(jsonPath("$.decisions.length()").value(0))
        .andExpect(jsonPath("$.snapshot_taken").value(false));
    mvc.perform(get("/api/v1/schedule-proposals/" + id).header("Authorization", stranger.bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/schedule-proposals/" + id).header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/schedule-proposals/not-a-uuid").header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theBoardApprovesOnceWithTheVersionItSaw() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.approve", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("proposal.approve.op", "OPERATOR", aqueduct));
    String id = generatedId(member, TOMORROW);

    decide(operator, id, "approve", "{\"lock_version\":0}").andExpect(status().isForbidden());
    decide(member, id, "approve", "{\"lock_version\":3}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    decide(member, id, "approve", "{}").andExpect(status().isBadRequest());
    decide(member, id, "approve", "{\"lock_version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.lock_version").value(1))
        .andExpect(jsonPath("$.decisions[0].decision").value("APPROVED"))
        .andExpect(jsonPath("$.decisions[0].reason").doesNotExist());
    decide(member, id, "approve", "{\"lock_version\":1}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    decide(member, UUID.randomUUID().toString(), "approve", "{\"lock_version\":0}")
        .andExpect(status().isNotFound());
  }

  static String at(int hour) {
    return TOMORROW
        .atTime(hour, 0)
        .atZone(ZoneId.of("America/Bogota"))
        .toOffsetDateTime()
        .toString();
  }

  static String item(String id, UUID sector, String start, String end, int sequence) {
    return "{\"id\":"
        + (id == null ? "null" : "\"" + id + "\"")
        + ",\"sector_id\":\""
        + sector
        + "\",\"start_at\":\""
        + start
        + "\",\"end_at\":\""
        + end
        + "\",\"sequence\":"
        + sequence
        + "}";
  }

  static String modify(int lock, String reason, String... items) {
    return "{\"lock_version\":"
        + lock
        + ",\"reason\":\""
        + reason
        + "\",\"items\":["
        + String.join(",", items)
        + "]}";
  }

  @Test
  void theBoardChangesShiftsWithAReasonAndTheOriginalIsKept() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.modify", "BOARD_MEMBER", aqueduct));
    String body = generate(member, TOMORROW).andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");
    String altoId = JsonPath.read(body, "$.items[0].id");
    String altoStart = JsonPath.read(body, "$.items[0].start_at");
    String altoEnd = JsonPath.read(body, "$.items[0].end_at");
    String bajoId = JsonPath.read(body, "$.items[1].id");
    UUID alto = sectorOf(aqueduct, "ALTO");
    UUID bajo = sectorOf(aqueduct, "BAJO");
    String reason = "La escuela tiene clase especial en la tarde.";
    String keep = item(altoId, alto, altoStart, altoEnd, 1);

    decide(member, id, "modify", modify(0, "corto", keep))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    decide(member, id, "modify", modify(0, reason, keep, item(null, bajo, at(14), at(19), 2)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("SHIFT_LENGTH_INVALID"));
    decide(member, id, "modify", modify(0, reason, keep, item(null, bajo, at(3), at(6), 2)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("OUTSIDE_OPERATING_WINDOW"));
    decide(
            member,
            id,
            "modify",
            modify(
                0,
                reason,
                keep,
                item(bajoId, bajo, at(14), at(18), 2),
                item(null, alto, at(18), at(20), 3)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("HOURS_EXCEEDED"));
    decide(
            member,
            id,
            "modify",
            modify(0, reason, keep, item(UUID.randomUUID().toString(), bajo, at(14), at(17), 2)))
        .andExpect(status().isBadRequest());

    decide(
            member,
            id,
            "modify",
            modify(
                0,
                reason,
                keep,
                item(bajoId, bajo, at(14), at(17), 2),
                item(null, alto, at(18), at(19), 3)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED_WITH_CHANGES"))
        .andExpect(jsonPath("$.lock_version").value(1))
        .andExpect(jsonPath("$.snapshot_taken").value(true))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].origin").value("PROPOSED"))
        .andExpect(jsonPath("$.items[1].origin").value("MODIFIED"))
        .andExpect(jsonPath("$.items[1].reasons[-1].code").value("BOARD_CHANGE"))
        .andExpect(
            jsonPath("$.items[1].reasons[-1].text").value("La Junta cambió este turno: " + reason))
        .andExpect(jsonPath("$.items[2].origin").value("ADDED"))
        .andExpect(jsonPath("$.items[2].reasons.length()").value(1))
        .andExpect(jsonPath("$.decisions[0].decision").value("APPROVED_WITH_CHANGES"))
        .andExpect(jsonPath("$.decisions[0].reason").value(reason));
    org.assertj.core.api.Assertions.assertThat(
            owner.queryForObject(
                "SELECT jsonb_array_length(snapshot -> 'items') FROM ops.proposal_snapshots"
                    + " WHERE proposal_id = ?::uuid",
                Integer.class,
                id))
        .isEqualTo(2);
    decide(member, id, "reject", "{\"lock_version\":1,\"reason\":\"" + reason + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void aRejectedProposalNeedsAReasonAndFreesTheDay() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.reject", "BOARD_MEMBER", aqueduct));
    String id = generatedId(member, TOMORROW);
    String reason = "El nivel bajó en la tarde; se genera otra.";

    decide(member, id, "reject", "{\"lock_version\":0}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    decide(member, id, "reject", "{\"lock_version\":0,\"reason\":\"" + reason + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.decisions[0].reason").value(reason));
    decide(
            member,
            id,
            "modify",
            modify(1, reason, item(null, sectorOf(aqueduct, "ALTO"), at(7), at(9), 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    generate(member, TOMORROW).andExpect(status().isCreated());
  }

  @Test
  void overlappingShiftsAreRefusedByTheDatabase() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("proposal.overlap", "BOARD_MEMBER", aqueduct));
    String id = generatedId(member, TOMORROW);
    String reason = "Se cruzan a propósito para la prueba.";

    decide(
            member,
            id,
            "modify",
            modify(
                0,
                reason,
                item(null, sectorOf(aqueduct, "ALTO"), at(7), at(9), 1),
                item(null, sectorOf(aqueduct, "BAJO"), at(8), at(10), 2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SCHEDULE_OVERLAP"));
    mvc.perform(get("/api/v1/schedule-proposals/" + id).header("Authorization", member.bearer()))
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.snapshot_taken").value(false));
  }
}
