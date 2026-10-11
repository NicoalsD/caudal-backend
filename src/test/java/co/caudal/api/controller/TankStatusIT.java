package co.caudal.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The status of a tank: level, trend, staleness, band and anomalies. */
class TankStatusIT extends SessionIT {

  private void reading(UUID aqueduct, UUID tank, String value, Instant at) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?, 'ACCEPTED')",
        aqueduct,
        tank,
        value,
        java.sql.Timestamp.from(at),
        java.sql.Timestamp.from(at),
        ruleSet);
  }

  @Test
  void aTankGoingDownShowsItsTrendAndBand() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    Instant now = Instant.now();
    reading(aqueduct, tank, "2.60", now.minus(2, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS));
    reading(aqueduct, tank, "2.35", now.minus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS));
    reading(aqueduct, tank, "2.10", now.minus(1, ChronoUnit.HOURS));
    Session session = login(account("status.down", "OPERATOR", aqueduct));

    mvc.perform(get("/api/v1/tanks/" + tank + "/status").header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level.value").value(2.10))
        .andExpect(jsonPath("$.trend.label").value("DOWN"))
        .andExpect(jsonPath("$.trend.label_es").value("viene bajando"))
        .andExpect(jsonPath("$.stale.is_stale").value(false))
        .andExpect(jsonPath("$.band").value("LOW"));
  }

  @Test
  void anOldReadingIsStaleAndOpensOneAnomaly() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    reading(aqueduct, tank, "5.00", Instant.now().minus(20, ChronoUnit.HOURS));
    Session session = login(account("status.stale", "BOARD_MEMBER", aqueduct));

    for (int i = 0; i < 2; i++) {
      mvc.perform(
              get("/api/v1/tanks/" + tank + "/status").header("Authorization", session.bearer()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.stale.is_stale").value(true))
          .andExpect(jsonPath("$.band").value("HIGH"))
          .andExpect(jsonPath("$.open_anomalies").value(1));
    }
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.anomalies WHERE aqueduct_id = ? AND kind = 'STALE_DATA'",
                Integer.class,
                aqueduct))
        .isEqualTo(1);
  }

  @Test
  void aTankWithoutReadingsHasNoLevelAndAnotherAqueductIsHidden() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("status.empty", "OPERATOR", aqueduct));

    mvc.perform(
            get("/api/v1/tanks/" + newTank(aqueduct) + "/status")
                .header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").doesNotExist())
        .andExpect(jsonPath("$.stale.is_stale").value(false));
    mvc.perform(
            get("/api/v1/tanks/" + newTank(newAqueductWithRules()) + "/status")
                .header("Authorization", session.bearer()))
        .andExpect(status().isNotFound());
  }
}
