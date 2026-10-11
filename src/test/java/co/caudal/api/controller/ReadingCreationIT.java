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

  @Test
  void theSameValueWithAnotherIdInsideTheWindowIsFlaggedDuplicate() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.duplicate", "OPERATOR", demoAqueduct()));
    UUID first = UUID.randomUUID();
    record(session, first, tank, "2.10", NOW.minus(60, ChronoUnit.MINUTES))
        .andExpect(status().isCreated());

    record(session, UUID.randomUUID(), tank, "2.10", NOW.minus(40, ChronoUnit.MINUTES))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reading.validation_status").value("FLAGGED"))
        .andExpect(jsonPath("$.issues[0].code").value("DUPLICATE"))
        .andExpect(jsonPath("$.issues[0].details.matched_reading_id").value(first.toString()));
  }

  @Test
  void aFastDropIsFlaggedSuddenJump() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.jump", "OPERATOR", demoAqueduct()));
    record(session, UUID.randomUUID(), tank, "3.50", NOW.minus(2, ChronoUnit.HOURS))
        .andExpect(status().isCreated());

    record(session, UUID.randomUUID(), tank, "1.00", NOW.minus(1, ChronoUnit.HOURS))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reading.validation_status").value("FLAGGED"))
        .andExpect(jsonPath("$.issues[0].code").value("SUDDEN_JUMP"))
        .andExpect(jsonPath("$.issues[0].severity").value("WARNING"));
  }

  @Test
  void theSameIdAndContentIsARetryAndOtherContentIsAConflict() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.retry", "OPERATOR", demoAqueduct()));
    UUID id = UUID.randomUUID();
    Instant at = NOW.minus(30, ChronoUnit.MINUTES);

    record(session, id, tank, "2.10", at)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.idempotent_replay").value(false));
    record(session, id, tank, "2.1", at)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idempotent_replay").value(true))
        .andExpect(jsonPath("$.reading.id").value(id.toString()));
    record(session, id, tank, "2.20", at)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_READING"));
    org.assertj.core.api.Assertions.assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.readings WHERE id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void aReadingIsShownWithItsIssuesAndNotOutsideItsAqueduct() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("create.detail", "OPERATOR", demoAqueduct()));
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    record(session, first, tank, "2.10", NOW.minus(60, ChronoUnit.MINUTES))
        .andExpect(status().isCreated());
    record(session, second, tank, "2.10", NOW.minus(50, ChronoUnit.MINUTES))
        .andExpect(status().isCreated());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/readings/" + second)
                .header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reading.id").value(second.toString()))
        .andExpect(jsonPath("$.reading.effective_gauge_value").value(2.10))
        .andExpect(jsonPath("$.reading.correction_count").value(0))
        .andExpect(jsonPath("$.issues[0].code").value("DUPLICATE"))
        .andExpect(jsonPath("$.corrections.length()").value(0));
    Session stranger = login(account("create.detail.out", "OPERATOR"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/readings/" + second)
                .header("Authorization", stranger.bearer()))
        .andExpect(status().isNotFound());
  }
}
