package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Registering incidents from the panel end to end (docs/API.md, section 12.2). */
class IncidentCreationIT extends SessionIT {

  UUID sector(UUID aqueduct, String code) {
    return owner.queryForObject(
        "SELECT id FROM org.sectors WHERE aqueduct_id = ? AND code = ?",
        UUID.class,
        aqueduct,
        code);
  }

  ResultActions create(Session session, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/incidents")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  static String body(String category, String sector, String description, String location) {
    return "{\"category_code\":\""
        + category
        + "\",\"sector_id\":"
        + (sector == null ? "null" : "\"" + sector + "\"")
        + ",\"description\":\""
        + description
        + "\",\"location_hint\":\""
        + location
        + "\"}";
  }

  @Test
  void anOperatorRegistersAnIncidentReportedAndAudited() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID alto = sector(aqueduct, "ALTO");
    Session operator = login(account("incident.operator", "OPERATOR", aqueduct));

    String response =
        create(
                operator,
                body(
                    "LEAK",
                    alto.toString(),
                    "Gotea el tubo junto a la escuela.",
                    "Frente a la escuela"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("OPERATOR"))
            .andExpect(jsonPath("$.category_code").value("LEAK"))
            .andExpect(jsonPath("$.sector_id").value(alto.toString()))
            .andExpect(jsonPath("$.status").value("REPORTED"))
            .andExpect(jsonPath("$.location_hint").value("Frente a la escuela"))
            .andExpect(jsonPath("$.reporter_ip_hmac").doesNotExist())
            .andExpect(jsonPath("$.tracking_code_hash").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

    assertThat(
            owner.queryForObject(
                "SELECT reporter_ip_hmac IS NULL FROM ops.incidents WHERE id = ?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.incident_status_history WHERE incident_id = ?"
                    + " AND from_status IS NULL AND to_status = 'REPORTED' AND changed_by = ?",
                Integer.class,
                id,
                operator.userId()))
        .isEqualTo(1);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM audit.audit_log WHERE action = 'CREATE_INCIDENT'"
                    + " AND entity_type = 'incident' AND entity_id = ? AND actor_id = ?",
                Integer.class,
                id.toString(),
                operator.userId()))
        .isEqualTo(1);
  }

  @Test
  void theBoardMemberRegistersWithoutSectorAndTheSupportEntityIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session board = login(account("incident.board", "BOARD_MEMBER", aqueduct));
    Session support = login(account("incident.entity", "SUPPORT_ENTITY", aqueduct));

    create(board, body("LEAK", null, "Sin agua en la vereda alta.", ""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sector_id").doesNotExist());
    create(support, body("LEAK", null, "Sin agua en la vereda alta.", ""))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidCategorySectorOrTextAreRejected() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID otherSector = sector(newAqueductWithRules(), "ALTO");
    Session operator = login(account("incident.checks", "OPERATOR", aqueduct));

    create(operator, body("NOPE", null, "Gotea el tubo junto a la escuela.", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    create(operator, body("LEAK", otherSector.toString(), "Gotea el tubo junto a la escuela.", ""))
        .andExpect(status().isBadRequest());
    create(operator, body("LEAK", null, "corto", "")).andExpect(status().isBadRequest());
    create(operator, body("LEAK", null, "Gotea el tubo junto a la escuela.", "x".repeat(121)))
        .andExpect(status().isBadRequest());
  }
}
