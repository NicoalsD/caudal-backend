package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.PublishedScheduleIT;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Publishing an approved schedule and showing it to the public. */
class PublicationIT extends PublishedScheduleIT {

  @Test
  void anApprovedScheduleIsPublishedOnceWithoutPersonalData() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("publish.member", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("publish.operator", "OPERATOR", aqueduct));
    String id = approvedProposal(member);

    decide(operator, id, "publish", "{\"lock_version\":1}").andExpect(status().isForbidden());
    decide(member, id, "publish", "{\"lock_version\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    String body =
        decide(member, id, "publish", "{\"lock_version\":1}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.proposal_id").value(id))
            .andExpect(jsonPath("$.published_at").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String text = JsonPath.read(body, "$.whatsapp_text");
    assertThat(text)
        .contains("Horario de agua de")
        .contains("- Sector ALTO: de ")
        .contains("- Sector BAJO: de ")
        .doesNotContain("publish.member")
        .doesNotContain("2.10");
    decide(member, id, "publish", "{\"lock_version\":2}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    assertThat(
            owner.queryForObject(
                "SELECT status FROM ops.schedule_proposals WHERE id = ?::uuid", String.class, id))
        .isEqualTo("PUBLISHED");
  }

  @Test
  void aProposalPendingReviewIsNotPublished() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("publish.pending", "BOARD_MEMBER", aqueduct));
    String body =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/schedule-proposals")
                    .header("Authorization", member.bearer())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"service_date\":\"" + TOMORROW + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    decide(member, JsonPath.read(body, "$.id"), "publish", "{\"lock_version\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }
}
