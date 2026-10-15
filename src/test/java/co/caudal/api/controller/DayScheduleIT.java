package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.PublishedScheduleIT;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The published schedule of a day, read by the operator to register the executions. */
class DayScheduleIT extends PublishedScheduleIT {

  private static final String DAY = "/api/v1/days/" + TOMORROW + "/schedule";

  @Test
  void anOperatorReadsThePublishedDayAndRegistersAnExecutionWithItsIds() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("day.schedule.member", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("day.schedule.operator", "OPERATOR", aqueduct));
    String proposal = JsonPath.read(publishedSchedule(member), "$.proposal_id");

    String body =
        mvc.perform(get(DAY).header("Authorization", operator.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service_date").value(TOMORROW.toString()))
            .andExpect(jsonPath("$.proposal_id").value(proposal))
            .andExpect(jsonPath("$.published_at").isNotEmpty())
            .andExpect(jsonPath("$.items[0].id").isNotEmpty())
            .andExpect(jsonPath("$.items[0].sector_id").isNotEmpty())
            .andExpect(jsonPath("$.items[0].start_at").isNotEmpty())
            .andExpect(jsonPath("$.items[0].reasons").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String shift = JsonPath.read(body, "$.items[0].id");

    mvc.perform(
            post("/api/v1/schedule-items/" + shift + "/execution")
                .header("Authorization", operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void anApprovedButUnpublishedDayAnswers404() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("day.schedule.approved", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("day.schedule.approved.op", "OPERATOR", aqueduct));
    approvedProposal(member);

    mvc.perform(get(DAY).header("Authorization", operator.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void aDayWithoutAProposalAnswers404() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session operator = login(account("day.schedule.empty.op", "OPERATOR", aqueduct));

    mvc.perform(get(DAY).header("Authorization", operator.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void aScheduleOfAnotherAqueductAnswers404() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("day.schedule.owner", "BOARD_MEMBER", aqueduct));
    publishedSchedule(member);

    UUID other = newAqueduct();
    Session outsider = login(account("day.schedule.other", "OPERATOR", other));

    mvc.perform(get(DAY).header("Authorization", outsider.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
