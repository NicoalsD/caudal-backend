package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Reviewing anomalies end to end. */
class AnomalyIT extends SessionIT {

  UUID anomaly(UUID aqueduct) {
    return owner.queryForObject(
        "INSERT INTO ops.anomalies (aqueduct_id, kind, evidence) VALUES (?, 'POSSIBLE_LEAK',"
            + " '{\"drop\": 0.15}'::jsonb) RETURNING id",
        UUID.class,
        aqueduct);
  }

  ResultActions review(Session session, UUID id, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/anomalies/" + id)
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void aMemberDismissesWithAReasonAndItNoLongerChanges() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = anomaly(aqueduct);
    Session member = login(account("anomaly.member", "BOARD_MEMBER", aqueduct));

    mvc.perform(get("/api/v1/anomalies?status=OPEN").header("Authorization", member.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].kind").value("POSSIBLE_LEAK"))
        .andExpect(jsonPath("$.items[0].evidence.drop").value(0.15));
    review(member, id, "{\"status\":\"DISMISSED\",\"resolution_note\":\"corto\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    review(
            member,
            id,
            "{\"status\":\"DISMISSED\",\"resolution_note\":\"La caída fue por la lluvia.\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"))
        .andExpect(jsonPath("$.resolved_by").value(member.userId().toString()));
    review(member, id, "{\"status\":\"CONFIRMED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
  }

  @Test
  void anOperatorCannotReviewAndOtherAqueductsAreHidden() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = anomaly(aqueduct);
    Session operator = login(account("anomaly.operator", "OPERATOR", aqueduct));
    Session stranger = login(account("anomaly.stranger", "BOARD_MEMBER"));

    review(operator, id, "{\"status\":\"CONFIRMED\"}").andExpect(status().isForbidden());
    review(stranger, id, "{\"status\":\"CONFIRMED\"}").andExpect(status().isNotFound());
    review(stranger, id, "{\"status\":\"OPEN\"}").andExpect(status().isBadRequest());
  }
}
