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

/** Recording a reading end to end through the validation chain. */
class ReadingCreationIT extends SessionIT {

  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  ResultActions record(Session session, UUID id, UUID tank, String value, Instant observedAt)
      throws Exception {
    return mvc.perform(
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
                    + ",\"water_appearance_code\":\"NORMAL\""
                    + (observedAt == null ? "" : ",\"observed_at\":\"" + observedAt + "\"")
                    + "}"));
  }

  @Test
  void aValueOnTheGaugeIsAccepted() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.accepted", "OPERATOR", demoAqueduct()));

    record(session, UUID.randomUUID(), tank, "2.10", NOW.minus(1, ChronoUnit.HOURS))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reading.validation_status").value("ACCEPTED"))
        .andExpect(jsonPath("$.reading.source").value("MANUAL_APP"))
        .andExpect(jsonPath("$.issues.length()").value(0));
  }

  @Test
  void aValueOffTheGaugeIsRejectedAndNotStored() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.range", "OPERATOR", demoAqueduct()));
    UUID id = UUID.randomUUID();

    record(session, id, tank, "8.00", NOW.minus(1, ChronoUnit.HOURS))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("GAUGE_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.error.details.gauge_max").value("5.00"));
    record(session, UUID.randomUUID(), tank, "2.105", NOW.minus(1, ChronoUnit.HOURS))
        .andExpect(status().isUnprocessableContent());
    org.assertj.core.api.Assertions.assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.readings WHERE id = ?", Integer.class, id))
        .isZero();
  }

  @Test
  void aMissingFutureOrOldMomentIsRejected() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.moment", "OPERATOR", demoAqueduct()));

    record(session, UUID.randomUUID(), tank, "2.10", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("MISSING_TIMESTAMP"));
    record(session, UUID.randomUUID(), tank, "2.10", NOW.plus(1, ChronoUnit.HOURS))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("FUTURE_TIMESTAMP"));
    record(session, UUID.randomUUID(), tank, "2.10", NOW.minus(20, ChronoUnit.DAYS))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("TOO_OLD"))
        .andExpect(jsonPath("$.error.details.max_backdate_days").value(7));
  }
}
