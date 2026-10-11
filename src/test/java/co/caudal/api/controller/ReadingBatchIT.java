package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The offline queue end to end: per-reading results, time order and batch idempotency. */
class ReadingBatchIT extends SessionIT {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  private static String reading(UUID id, UUID tank, String value, Instant at) {
    return "{\"id\":\""
        + id
        + "\",\"tank_id\":\""
        + tank
        + "\",\"gauge_value\":"
        + value
        + ",\"water_appearance_code\":\"NORMAL\",\"observed_at\":\""
        + at
        + "\"}";
  }

  private ResultActions send(Session session, UUID batch, List<String> readings) throws Exception {
    return mvc.perform(
        post("/api/v1/readings/batch")
            .header("Authorization", session.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"sync_batch_id\":\""
                    + batch
                    + "\",\"readings\":["
                    + readings.stream().collect(Collectors.joining(","))
                    + "]}"));
  }

  @Test
  void eachReadingIsJudgedAloneAndTheBatchIsStored() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("batch.main", "OPERATOR", demoAqueduct()));
    UUID batch = UUID.randomUUID();
    UUID early = UUID.randomUUID();
    UUID late = UUID.randomUUID();
    UUID bad = UUID.randomUUID();
    UUID jump = UUID.randomUUID();
    List<String> readings =
        List.of(
            reading(early, tank, "3.00", NOW.minus(3, ChronoUnit.HOURS)),
            reading(late, tank, "2.95", NOW.minus(2, ChronoUnit.HOURS)),
            reading(bad, tank, "9.00", NOW.minus(2, ChronoUnit.HOURS)),
            reading(jump, tank, "1.00", NOW.minus(1, ChronoUnit.HOURS)));

    send(session, batch, readings)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items_received").value(4))
        .andExpect(jsonPath("$.items_accepted").value(3))
        .andExpect(jsonPath("$.items_rejected").value(1))
        .andExpect(jsonPath("$.idempotent_replay").value(false))
        .andExpect(jsonPath("$.results[0].id").value(early.toString()))
        .andExpect(jsonPath("$.results[0].status").value("ACCEPTED"))
        .andExpect(jsonPath("$.results[1].status").value("ACCEPTED"))
        .andExpect(jsonPath("$.results[2].status").value("REJECTED"))
        .andExpect(jsonPath("$.results[2].error.code").value("GAUGE_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.results[3].status").value("FLAGGED"));

    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.readings WHERE sync_batch_id = ?", Integer.class, batch))
        .isEqualTo(3);
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.sync_batches WHERE id = ?", Integer.class, batch))
        .isEqualTo(1);
  }

  @Test
  void reusingABatchIdWithOtherReadingsIsAConflict() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("batch.reuse", "OPERATOR", demoAqueduct()));
    UUID batch = UUID.randomUUID();
    send(session, batch, List.of(reading(UUID.randomUUID(), tank, "2.00", NOW.minusSeconds(600))))
        .andExpect(status().isOk());

    send(session, batch, List.of(reading(UUID.randomUUID(), tank, "2.00", NOW.minusSeconds(300))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.details.reason").value("SYNC_BATCH_REUSED"));
    send(
            session,
            batch,
            List.of(
                reading(UUID.randomUUID(), tank, "2.00", NOW.minusSeconds(300)),
                reading(UUID.randomUUID(), tank, "2.00", NOW.minusSeconds(200))))
        .andExpect(status().isConflict());
  }

  @Test
  void repeatedIdsAnEmptyBatchAndAnUnknownFieldAreInvalid() throws Exception {
    UUID tank = newTank(demoAqueduct());
    Session session = login(account("batch.invalid", "OPERATOR", demoAqueduct()));
    UUID id = UUID.randomUUID();

    send(
            session,
            UUID.randomUUID(),
            List.of(
                reading(id, tank, "2.00", NOW.minusSeconds(600)),
                reading(id, tank, "2.00", NOW.minusSeconds(600))))
        .andExpect(status().isBadRequest());
    send(session, UUID.randomUUID(), List.of()).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/readings/batch")
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sync_batch_id\":\"" + UUID.randomUUID() + "\",\"readings\":[],\"x\":1}"))
        .andExpect(status().isBadRequest());
  }
}
