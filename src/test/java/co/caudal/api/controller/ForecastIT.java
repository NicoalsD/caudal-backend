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

  private void reading(UUID aqueduct, UUID tank, String value, Instant at) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    owner.update(
        "INSERT INTO ops.readings (id, aqueduct_id, tank_id, gauge_value, water_appearance_code,"
            + " observed_at, received_at, source, rule_set_id, validation_status)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::numeric, 'NORMAL', ?, ?, 'IMPORT', ?,"
            + " 'ACCEPTED')",
        aqueduct,
        tank,
        value,
        java.sql.Timestamp.from(at),
        java.sql.Timestamp.from(at),
        ruleSet);
  }

  @Test
  void withoutTheServiceTheSimpleEstimateIsStoredAsTheReplacement() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    Instant now = Instant.now();
    String[] values = {"3.00", "2.90", "2.80", "2.70", "2.60"};
    for (int i = 0; i < values.length; i++) {
      reading(aqueduct, tank, values[i], now.minus(values.length - i, ChronoUnit.DAYS));
    }
    Session session = login(account("forecast.run", "BOARD_MEMBER", aqueduct));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/tanks/" + tank + "/forecasts")
                .header("Authorization", session.bearer())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"horizon_days\":2}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.model_name").value("naive-persistence"))
        .andExpect(jsonPath("$.is_fallback").value(true))
        .andExpect(jsonPath("$.fallback_reason").value("IA_ERROR"))
        .andExpect(jsonPath("$.horizon_days").value(2))
        .andExpect(jsonPath("$.points.length()").value(2))
        .andExpect(jsonPath("$.points[0].p50").value(2.50));
    org.assertj.core.api.Assertions.assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM ops.forecast_runs WHERE tank_id = ?", Integer.class, tank))
        .isEqualTo(1);
  }

  @Test
  void noRecentReadingABadHorizonAndAnOperatorAreRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    Session member = login(account("forecast.refused", "BOARD_MEMBER", aqueduct));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/tanks/" + tank + "/forecasts")
                .header("Authorization", member.bearer())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("NO_LEVEL_DATA"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/tanks/" + tank + "/forecasts")
                .header("Authorization", member.bearer())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"horizon_days\":9}"))
        .andExpect(status().isBadRequest());
    Session operator = login(account("forecast.operator", "OPERATOR", aqueduct));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/tanks/" + tank + "/forecasts")
                .header("Authorization", operator.bearer())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }
}
