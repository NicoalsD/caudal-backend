package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.PublishedScheduleIT;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Minutes of the Board: generation, sections, finalization and PDF. */
class MinutesIT extends PublishedScheduleIT {

  static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

  ResultActions generate(Session session, LocalDate from, LocalDate to) throws Exception {
    return mvc.perform(
        post("/api/v1/minutes")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"period_from\":\"" + from + "\",\"period_to\":\"" + to + "\"}"));
  }

  /** A period with one record of each kind; returns the id of the generated minutes. */
  String minutesWithEveryKind(UUID aqueduct, Session member, Session operator) throws Exception {
    reading(aqueduct, "2.10");
    String proposal = JsonPath.read(publishedSchedule(member), "$.proposal_id");
    UUID item =
        owner.queryForObject(
            "SELECT id FROM ops.schedule_items WHERE proposal_id = ?::uuid ORDER BY sequence"
                + " LIMIT 1",
            UUID.class,
            proposal);
    mvc.perform(
            post("/api/v1/schedule-items/" + item + "/execution")
                .header("Authorization", operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isCreated());
    owner.update(
        "INSERT INTO ops.anomalies (aqueduct_id, kind, evidence) VALUES (?, 'POSSIBLE_LEAK',"
            + " '{}'::jsonb)",
        aqueduct);
    for (String state : new String[] {"REPORTED", "CONFIRMED"}) {
      owner.update(
          "INSERT INTO ops.incidents (aqueduct_id, source, category_code, description, status,"
              + " tracking_code_hash) VALUES (?, 'OPERATOR', 'LEAK', 'Gotea el tubo de la"
              + " escuela.', ?, encode(digest(gen_random_uuid()::text, 'sha256'), 'hex'))",
          aqueduct,
          state);
    }
    String body =
        generate(member, TODAY.minusDays(1), TODAY.plusDays(1))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void theMinutesPlaceEachRecordInItsSection() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.member", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("minutes.operator", "OPERATOR", aqueduct));
    String id = minutesWithEveryKind(aqueduct, member, operator);

    String body =
        mvc.perform(get("/api/v1/minutes/" + id).header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period_from").value(TODAY.minusDays(1).toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> observed = JsonPath.read(body, "$.content.observed[*].source");
    List<String> estimated = JsonPath.read(body, "$.content.estimated[*].source");
    List<String> inferred = JsonPath.read(body, "$.content.inferred[*].source");
    List<String> confirmed = JsonPath.read(body, "$.content.confirmed[*].source");

    assertThat(observed).contains("reading", "incident");
    assertThat(estimated).contains("proposal");
    assertThat(inferred).containsExactly("anomaly");
    assertThat(confirmed).containsExactlyInAnyOrder("execution", "incident");
    assertThat(body).doesNotContain("escuela").doesNotContain("minutes.member");
    List<String> sentences = JsonPath.read(body, "$.content.observed[*].summary");
    assertThat(sentences).contains("Lectura del tanque: 2,10 (ACCEPTED).");
  }

  @Test
  void anInvertedPeriodOrAnotherRoleIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.bad", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("minutes.bad.op", "OPERATOR", aqueduct));

    generate(member, TODAY, TODAY.minusDays(1)).andExpect(status().isBadRequest());
    generate(member, TODAY.minusYears(2), TODAY).andExpect(status().isBadRequest());
    generate(operator, TODAY, TODAY).andExpect(status().isForbidden());
    generate(member, TODAY, TODAY)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content.confirmed.length()").value(0));
    mvc.perform(
            get("/api/v1/minutes/" + UUID.randomUUID()).header("Authorization", member.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void aDraftIsFinalizedOnceWithTheHeadOfTheAuditChain() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("minutes.final", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("minutes.final.op", "OPERATOR", aqueduct));
    String id = minutesWithEveryKind(aqueduct, member, operator);

    mvc.perform(
            post("/api/v1/minutes/" + id + "/finalize").header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/minutes/" + id + "/finalize").header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINAL"))
        .andExpect(jsonPath("$.finalized_by").value(member.userId().toString()))
        .andExpect(jsonPath("$.finalized_at").isNotEmpty())
        .andExpect(
            jsonPath("$.audit_head_hash")
                .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")));
    mvc.perform(
            post("/api/v1/minutes/" + id + "/finalize").header("Authorization", member.bearer()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    mvc.perform(
            post("/api/v1/minutes/" + UUID.randomUUID() + "/finalize")
                .header("Authorization", member.bearer()))
        .andExpect(status().isNotFound());
  }
}
