package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Changing the status of incidents end to end (docs/API.md, section 12.3). */
class IncidentStatusIT extends SessionIT {

  UUID incident(UUID aqueduct, String status) {
    return owner.queryForObject(
        "INSERT INTO ops.incidents (aqueduct_id, source, category_code, description, status,"
            + " tracking_code_hash, reported_at) VALUES (?, 'OPERATOR', 'LEAK', 'Gotea el tubo"
            + " junto a la escuela.', ?, encode(sha256(convert_to(gen_random_uuid()::text,"
            + " 'UTF8')), 'hex'), ?) RETURNING id",
        UUID.class,
        aqueduct,
        status,
        OffsetDateTime.ofInstant(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
  }

  ResultActions change(Session session, UUID id, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/incidents/" + id + "/status")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void theBoardFollowsTheRulesAndEachChangeIsInTheHistory() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "REPORTED");
    Session board = login(account("incident.status.board", "BOARD_MEMBER", aqueduct));

    change(board, id, "{\"status\":\"VERIFYING\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFYING"));
    change(board, id, "{\"status\":\"DISMISSED\",\"note\":\"corto\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    change(
            board,
            id,
            "{\"status\":\"CONFIRMED\",\"note\":\"Se verificó en campo el 9 de octubre.\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    change(board, id, "{\"status\":\"DISMISSED\",\"note\":\"Ya no aplica al caso.\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    change(board, id, "{\"status\":\"RESOLVED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    change(board, id, "{\"status\":\"RESOLVED\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.incident_status_history WHERE incident_id = ?"
                    + " AND changed_by = ?",
                Integer.class,
                id,
                board.userId()))
        .isEqualTo(3);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.incident_status_history WHERE incident_id = ?"
                    + " AND from_status = 'VERIFYING' AND to_status = 'CONFIRMED'"
                    + " AND note = 'Se verificó en campo el 9 de octubre.'",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  void aReasonedDismissalFromReportedIsStored() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "REPORTED");
    Session board = login(account("incident.status.dismiss", "BOARD_ADMIN", aqueduct));

    change(board, id, "{\"status\":\"DISMISSED\",\"note\":\"Era agua de lluvia en la calle.\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
    assertThat(
            owner.queryForObject(
                "SELECT note FROM ops.incident_status_history WHERE incident_id = ?"
                    + " AND to_status = 'DISMISSED'",
                String.class,
                id))
        .isEqualTo("Era agua de lluvia en la calle.");
  }

  @Test
  void theOperatorAdvancesButCannotDismiss() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "REPORTED");
    UUID dismissable = incident(aqueduct, "REPORTED");
    Session operator = login(account("incident.status.operator", "OPERATOR", aqueduct));

    change(operator, id, "{\"status\":\"VERIFYING\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFYING"));
    change(operator, id, "{\"status\":\"CONFIRMED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    change(operator, id, "{\"status\":\"RESOLVED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    change(operator, dismissable, "{\"status\":\"DISMISSED\",\"note\":\"Era agua de lluvia.\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    assertThat(
            owner.queryForObject(
                "SELECT status FROM ops.incidents WHERE id = ?", String.class, dismissable))
        .isEqualTo("REPORTED");
  }

  @Test
  void theBoardMemberDismissesWithAReason() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "VERIFYING");
    Session board = login(account("incident.status.member", "BOARD_MEMBER", aqueduct));

    change(board, id, "{\"status\":\"DISMISSED\",\"note\":\"Era agua de lluvia.\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
  }

  @Test
  void invalidBodiesAreRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "REPORTED");
    Session board = login(account("incident.status.checks", "BOARD_MEMBER", aqueduct));

    change(board, id, "{\"status\":\"REPORTED\"}").andExpect(status().isBadRequest());
    change(board, id, "{\"status\":\"FOO\"}").andExpect(status().isBadRequest());
    change(board, id, "{\"note\":\"Sin estado.\"}").andExpect(status().isBadRequest());
    change(board, id, "{\"status\":\"VERIFYING\",\"note\":\"" + "x".repeat(501) + "\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void otherAqueductsAreHidden() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID id = incident(aqueduct, "REPORTED");
    Session stranger = login(account("incident.status.stranger", "BOARD_ADMIN"));

    change(stranger, id, "{\"status\":\"VERIFYING\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
