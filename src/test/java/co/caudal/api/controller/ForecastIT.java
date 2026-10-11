package co.caudal.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Forecasts of a tank end to end. */
class ForecastIT extends SessionIT {

  UUID run(UUID aqueduct, UUID tank, String model, boolean fallback, String reason, Instant at) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    UUID id =
        owner.queryForObject(
            "INSERT INTO ops.forecast_runs (aqueduct_id, tank_id, model_name, model_version,"
                + " is_fallback, fallback_reason, horizon_days, context_from, context_to,"
                + " input_points, rule_set_id, created_at) VALUES (?, ?, ?, '1', ?, ?, 1, ?, ?, 5,"
                + " ?, ?) RETURNING id",
            UUID.class,
            aqueduct,
            tank,
            model,
            fallback,
            reason,
            java.sql.Timestamp.from(at.minus(5, ChronoUnit.DAYS)),
            java.sql.Timestamp.from(at),
            ruleSet,
            java.sql.Timestamp.from(at));
    owner.update(
        "INSERT INTO ops.forecast_points (forecast_run_id, target_date, aqueduct_id, p10, p50, p90)"
            + " VALUES (?, CURRENT_DATE + 1, ?, 1.80, 2.10, 2.40)",
        id,
        aqueduct);
    return id;
  }

  @Test
  void theLatestForecastIsTheShownOneNeverTheShadow() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    Instant now = Instant.now();
    UUID shown =
        run(aqueduct, tank, "autogluon/chronos-2-small", false, null, now.minusSeconds(60));
    run(aqueduct, tank, "naive-persistence", false, "SHADOW_BASELINE", now.minusSeconds(30));
    Session session = login(account("forecast.latest", "OPERATOR", aqueduct));

    mvc.perform(
            get("/api/v1/tanks/" + tank + "/forecasts/latest")
                .header("Authorization", session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.run_id").value(shown.toString()))
        .andExpect(jsonPath("$.model_name").value("autogluon/chronos-2-small"))
        .andExpect(jsonPath("$.is_fallback").value(false))
        .andExpect(jsonPath("$.points[0].p50").value(2.10));
    mvc.perform(get("/api/v1/tanks/" + tank + "/status").header("Authorization", session.bearer()))
        .andExpect(jsonPath("$.forecast.run_id").value(shown.toString()));
  }

  @Test
  void aTankNeverForecastIsNotFound() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session session = login(account("forecast.none", "OPERATOR", aqueduct));

    mvc.perform(
            get("/api/v1/tanks/" + newTank(aqueduct) + "/forecasts/latest")
                .header("Authorization", session.bearer()))
        .andExpect(status().isNotFound());
  }
}
