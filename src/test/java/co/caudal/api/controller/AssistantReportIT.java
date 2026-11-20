package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The assistant and the reports without a caudal-ia service: the test profile has none, so every
 * answer comes from the deterministic rules (HU-43, HU-44).
 */
class AssistantReportIT extends SessionIT {

  private static final String ASK = "/api/v1/assistant/ask";

  private String ask(String question) {
    return "{\"question\":\"" + question + "\"}";
  }

  @Test
  void theAssistantAnswersWithRulesWhenThereIsNoService() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session operator = login(account("assistant.op", "OPERATOR", aqueduct));

    mvc.perform(
            post(ASK)
                .header("Authorization", operator.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ask("¿Alcanza el agua para mañana?")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("RULES"))
        .andExpect(jsonPath("$.model").value("rules-v1"))
        .andExpect(jsonPath("$.answer").isNotEmpty())
        .andExpect(jsonPath("$.generated_at").isNotEmpty());
  }

  @Test
  void theQuestionIsValidatedAndPermissionsApply() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session operator = login(account("assistant.val", "OPERATOR", aqueduct));
    Session support = login(account("assistant.support", "SUPPORT_ENTITY", aqueduct));

    for (String bad : new String[] {ask("ab"), ask("x".repeat(501)), "{}", "{\"question\":null}"}) {
      mvc.perform(
              post(ASK)
                  .header("Authorization", operator.bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
    mvc.perform(
            post(ASK)
                .header("Authorization", support.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ask("¿Cómo está el tanque?")))
        .andExpect(status().isForbidden());
  }

  @Test
  void theReportGathersThePeriodAndTheNarrativeFallsBackToATemplate() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("report.member", "BOARD_MEMBER", aqueduct));
    Instant to = Instant.now();
    Instant from = to.minusSeconds(7 * 86_400L);

    mvc.perform(
            get("/api/v1/reports?from=" + from + "&to=" + to)
                .header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").isNotEmpty())
        .andExpect(jsonPath("$.aggregates.levels.readings").value(0))
        .andExpect(jsonPath("$.aggregates.shifts_by_sector").isArray())
        .andExpect(jsonPath("$.aggregates.valve_commands").isArray())
        .andExpect(jsonPath("$.aggregates.autopilot_decisions").isArray());
    mvc.perform(
            post("/api/v1/reports/narrative")
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("RULES"))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.startsWith("# Reporte")))
        .andExpect(jsonPath("$.findings").isArray());
  }

  @Test
  void theReportPeriodIsValidatedAndTheViewPermissionIsRequired() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session member = login(account("report.val", "BOARD_MEMBER", aqueduct));
    Session operator = login(account("report.op", "OPERATOR", aqueduct));
    Instant now = Instant.now();

    mvc.perform(
            get("/api/v1/reports?from=" + now + "&to=" + now.minusSeconds(60))
                .header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    mvc.perform(
            get("/api/v1/reports?from=" + now.minusSeconds(200L * 86_400) + "&to=" + now)
                .header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/reports?from=ayer&to=hoy").header("Authorization", member.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/reports?from=" + now.minusSeconds(60) + "&to=" + now)
                .header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
  }
}
