package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
            .andExpect(
                jsonPath("$.poster_sha256")
                    .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String text = JsonPath.read(body, "$.whatsapp_text");
    assertThat(text)
        .contains("Horario de agua de")
        .startsWith("*Horario de agua de")
        .contains("\n- Sector ALTO: de ")
        .contains("\n- Sector BAJO: de ")
        .doesNotContain("publish.member")
        .doesNotContain("2.10");
    decide(member, id, "publish", "{\"lock_version\":2}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    assertThat(
            owner.queryForObject(
                "SELECT status FROM ops.schedule_proposals WHERE id = ?::uuid", String.class, id))
        .isEqualTo("PUBLISHED");
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE aqueduct_id = ? AND action ="
                    + " 'SCHEDULE_PUBLISHED' AND after_state ->> 'proposal_id' = ?",
                Integer.class,
                aqueduct,
                id))
        .isEqualTo(1);
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

  String slugOf(UUID aqueduct) {
    return owner.queryForObject(
        "SELECT slug FROM org.aqueducts WHERE id = ?", String.class, aqueduct);
  }

  @Test
  void anyoneSeesThePublishedScheduleWithoutPersonalData() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    Session member = login(account("public.member", "BOARD_MEMBER", aqueduct));
    String publication = JsonPath.read(publishedSchedule(member), "$.id");
    String slug = slugOf(aqueduct);

    String body =
        mvc.perform(get("/api/v1/public/" + slug + "/schedule?date=" + TOMORROW))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service_date").value(TOMORROW.toString()))
            .andExpect(jsonPath("$.publication.id").value(publication))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].sector_name").value("Sector ALTO"))
            .andExpect(jsonPath("$.items[0].start_at").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain("published_by")
        .doesNotContain(member.userId().toString())
        .doesNotContain("reasons")
        .doesNotContain("tank");

    String text =
        mvc.perform(get("/api/v1/public/" + slug + "/schedule/whatsapp-text?date=" + TOMORROW))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .contentTypeCompatibleWith("text/plain"))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(text).startsWith("*Horario de agua de").contains("\n- Sector ALTO: de ");
    String stored =
        owner.queryForObject(
            "SELECT poster_sha256 FROM ops.publications WHERE id = ?::uuid",
            String.class,
            publication);
    byte[] pdf =
        mvc.perform(get("/api/v1/public/" + slug + "/schedule/poster.pdf?date=" + TOMORROW))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string("X-Content-SHA256", stored))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, 0, 8, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-1.4");
    assertThat(co.caudal.shared.hash.Sha256.hex(pdf)).isEqualTo(stored);
    mvc.perform(get("/api/v1/public/" + slug + "/schedule?date=" + TOMORROW.plusDays(1)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/public/no-existe/schedule")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/public/NO_VALIDO/schedule")).andExpect(status().isBadRequest());
    owner.update("UPDATE org.aqueducts SET public_page_enabled = false WHERE id = ?", aqueduct);
    mvc.perform(get("/api/v1/public/" + slug + "/schedule?date=" + TOMORROW))
        .andExpect(status().isNotFound());
  }

  @Test
  void anApprovedButUnpublishedScheduleIsNotPublic() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    reading(aqueduct, "2.10");
    approvedProposal(login(account("public.approved", "BOARD_MEMBER", aqueduct)));

    mvc.perform(get("/api/v1/public/" + slugOf(aqueduct) + "/schedule?date=" + TOMORROW))
        .andExpect(status().isNotFound());
  }
}
