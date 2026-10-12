package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Corrections of readings: who may correct, the gauge, the reason. */
class ReadingCorrectionIT extends SessionIT {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  UUID recordReading(Session session, UUID tank, String value) throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/readings")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":\""
                        + id
                        + "\",\"tank_id\":\""
                        + tank
                        + "\",\"gauge_value\":"
                        + value
                        + ",\"water_appearance_code\":\"NORMAL\",\"observed_at\":\""
                        + NOW.minus(1, ChronoUnit.HOURS)
                        + "\"}"))
        .andExpect(status().isCreated());
    return id;
  }

  ResultActions correct(Session session, UUID reading, String value, String reason)
      throws Exception {
    return mvc.perform(
        post("/api/v1/readings/" + reading + "/corrections")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"corrected_gauge_value\":"
                    + value
                    + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                    + "}"));
  }

  @Test
  void theAuthorCorrectsTheirReading() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session operator = login(account("correct.author", "OPERATOR", demoAqueduct()));
    UUID reading = recordReading(operator, tank, "2.10");

    correct(operator, reading, "2.00", "Leí mal la regla; lo confirmé con la foto.")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reading_id").value(reading.toString()))
        .andExpect(jsonPath("$.effective_gauge_value").value(2.00))
        .andExpect(jsonPath("$.corrected_by").value(operator.userId().toString()));
  }

  @Test
  void anOperatorCannotCorrectAReadingOfSomeoneElseButTheBoardCan() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session author = login(account("correct.owner", "OPERATOR", demoAqueduct()));
    Session other = login(account("correct.other", "OPERATOR", demoAqueduct()));
    Session board = login(account("correct.board", "BOARD_MEMBER", demoAqueduct()));
    UUID reading = recordReading(author, tank, "2.10");

    correct(other, reading, "2.00", "No es mía pero la corrijo.").andExpect(status().isForbidden());
    correct(board, reading, "2.05", "La Junta revisó la foto del tanque.")
        .andExpect(status().isCreated());
  }

  @Test
  void theValueMustBeOnTheGaugeAndTheReasonIsRequired() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session operator = login(account("correct.rules", "OPERATOR", demoAqueduct()));
    UUID reading = recordReading(operator, tank, "2.10");

    correct(operator, reading, "7.00", "Un motivo suficiente.")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("GAUGE_OUT_OF_RANGE"));
    correct(operator, reading, "2.00", "corto")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    correct(operator, reading, "2.00", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("REASON_REQUIRED"));
    correct(operator, UUID.randomUUID(), "2.00", "Un motivo suficiente.")
        .andExpect(status().isNotFound());
  }

  @Test
  void theLatestCorrectionWinsAndTheOriginalNeverChanges() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session operator = login(account("correct.latest", "OPERATOR", demoAqueduct()));
    UUID reading = recordReading(operator, tank, "2.10");
    correct(operator, reading, "2.00", "Primera corrección del valor.")
        .andExpect(status().isCreated());

    correct(operator, reading, "1.95", "Segunda corrección con la foto.")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.effective_gauge_value").value(1.95));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/readings/" + reading)
                .header("Authorization", operator.bearer()))
        .andExpect(jsonPath("$.reading.gauge_value").value(2.10))
        .andExpect(jsonPath("$.reading.effective_gauge_value").value(1.95))
        .andExpect(jsonPath("$.reading.correction_count").value(2))
        .andExpect(jsonPath("$.corrections[0].corrected_gauge_value").value(2.00))
        .andExpect(jsonPath("$.corrections[1].corrected_gauge_value").value(1.95));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/readings?tank_id=" + tank)
                .header("Authorization", operator.bearer()))
        .andExpect(jsonPath("$.items[0].gauge_value").value(2.10))
        .andExpect(jsonPath("$.items[0].effective_gauge_value").value(1.95));
    org.assertj.core.api.Assertions.assertThat(
            owner.queryForObject(
                "SELECT gauge_value FROM ops.readings WHERE id = ?",
                java.math.BigDecimal.class,
                reading))
        .isEqualByComparingTo("2.10");
  }
}
