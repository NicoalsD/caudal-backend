package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.PublishedScheduleIT;
import com.jayway.jsonpath.JsonPath;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Executions of shifts and the closure of the day. */
class ExecutionIT extends PublishedScheduleIT {

  List<UUID> items(String proposalId) {
    return owner.queryForList(
        "SELECT id FROM ops.schedule_items WHERE proposal_id = ?::uuid ORDER BY sequence",
        UUID.class,
        proposalId);
  }

  static String at(int hour) {
    return TOMORROW
        .atTime(hour, 0)
        .atZone(ZoneId.of("America/Bogota"))
        .toOffsetDateTime()
        .toString();
  }

  ResultActions execute(Session session, UUID item, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/schedule-items/" + item + "/execution")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void anExecutionIsRecordedOnceAndCorrectedWithANewRow() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("exec.member", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("exec.operator", "OPERATOR", aqueduct));
    String proposal = JsonPath.read(publishedSchedule(member), "$.proposal_id");
    UUID first = items(proposal).getFirst();

    String done =
        execute(operator, first, "{\"status\":\"COMPLETED\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.actual_start").isNotEmpty())
            .andExpect(jsonPath("$.actual_end").isNotEmpty())
            .andExpect(jsonPath("$.recorded_by").value(operator.userId().toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String doneId = JsonPath.read(done, "$.id");

    execute(operator, first, "{\"status\":\"NOT_EXECUTED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    String fix =
        "{\"status\":\"PARTIAL\",\"actual_start\":\""
            + at(5)
            + "\",\"actual_end\":\""
            + at(7)
            + "\",\"note\":\"Se cortó el agua a las 7 por la bomba.\",\"supersedes_id\":\""
            + doneId
            + "\"}";
    execute(operator, first, fix)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PARTIAL"))
        .andExpect(jsonPath("$.supersedes_id").value(doneId));
    execute(operator, first, fix)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
  }

  @Test
  void hoursAndReferencesFollowTheRules() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session board = login(account("exec.rules", "BOARD_MEMBER", aqueduct));
    Session member = login(account("exec.rules.op", "OPERATOR", aqueduct));
    Session team = login(account("exec.team", "PROJECT_TEAM", aqueduct));
    String proposal = JsonPath.read(publishedSchedule(board), "$.proposal_id");
    UUID item = items(proposal).get(1);

    execute(member, item, "{\"status\":\"PARTIAL\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("actual_start"));
    execute(member, item, "{\"status\":\"NOT_EXECUTED\",\"actual_start\":\"" + at(9) + "\"}")
        .andExpect(status().isBadRequest());
    execute(
            member,
            item,
            "{\"status\":\"COMPLETED\",\"actual_start\":\""
                + at(10)
                + "\",\"actual_end\":\""
                + at(9)
                + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.details.fields[0].field").value("actual_end"));
    execute(member, item, "{\"status\":\"DONE\"}").andExpect(status().isBadRequest());
    execute(member, item, "{\"status\":\"COMPLETED\",\"note\":\"" + "línea\\n".repeat(11) + "\"}")
        .andExpect(status().isBadRequest());
    execute(
            member,
            item,
            "{\"status\":\"COMPLETED\",\"supersedes_id\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isBadRequest());
    execute(team, item, "{\"status\":\"COMPLETED\"}").andExpect(status().isForbidden());
    execute(member, UUID.randomUUID(), "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void aScheduleThatIsNotPublishedTakesNoExecutions() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("exec.approved", "BOARD_MEMBER", aqueduct));
    Session admin = login(account("exec.approved.admin", "BOARD_ADMIN", aqueduct));
    String proposal = approvedProposal(member);

    execute(admin, items(proposal).getFirst(), "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }
}
