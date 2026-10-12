package co.caudal.api.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Evaluation of the AI against the simple estimate end to end. */
class EvaluationIT extends SessionIT {

  private static final ZoneId ZONE = ZoneId.of("America/Bogota");

  /** One forecast run with one point for the day after its context day. */
  UUID forecast(
      UUID aqueduct,
      UUID tank,
      String model,
      boolean fallback,
      String reason,
      LocalDate contextDay,
      String p10,
      String p50,
      String p90) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    java.time.Instant contextTo = at(contextDay);
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
            java.sql.Timestamp.from(contextTo.minus(5, ChronoUnit.DAYS)),
            java.sql.Timestamp.from(contextTo),
            ruleSet,
            java.sql.Timestamp.from(contextTo));
    owner.update(
        "INSERT INTO ops.forecast_points (forecast_run_id, target_date, aqueduct_id, p10, p50, p90)"
            + " VALUES (?, ?, ?, ?::numeric, ?::numeric, ?::numeric)",
        id,
        contextDay.plusDays(1),
        aqueduct,
        p10,
        p50,
        p90);
    return id;
  }

  /** A reading at noon of the given local day. */
  void reading(UUID aqueduct, UUID tank, String value, LocalDate day) {
    UUID ruleSet =
        owner.queryForObject(
            "SELECT id FROM org.rule_sets WHERE aqueduct_id = ? AND status = 'ACTIVE'",
            UUID.class,
            aqueduct);
    java.time.Instant at = at(day);
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

  /** Noon of the day in the aqueduct time zone (the default of new aqueducts). */
  private static java.time.Instant at(LocalDate day) {
    return day.atTime(LocalTime.NOON).atZone(ZONE).toInstant();
  }

  /** A day where the AI and the simple estimate both forecast, with the real level. */
  void comparableDay(
      UUID aqueduct,
      UUID tank,
      LocalDate contextDay,
      String actual,
      String p10,
      String p50,
      String p90,
      String simple) {
    forecast(aqueduct, tank, "autogluon/chronos-2-small", false, null, contextDay, p10, p50, p90);
    forecast(
        aqueduct,
        tank,
        "naive-persistence",
        false,
        "SHADOW_BASELINE",
        contextDay,
        simple,
        simple,
        simple);
    reading(aqueduct, tank, actual, contextDay.plusDays(1));
  }

  @Test
  void aProjectTeamSeesWhetherTheAiHelpsOnComparableDays() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    UUID tank = newTank(aqueduct);
    LocalDate today = LocalDate.now(ZONE);
    comparableDay(aqueduct, tank, today.minusDays(8), "2.00", "1.80", "2.10", "2.40", "2.60");
    comparableDay(aqueduct, tank, today.minusDays(7), "3.00", "2.50", "2.80", "3.10", "3.50");
    comparableDay(aqueduct, tank, today.minusDays(6), "2.50", "2.20", "2.45", "2.80", "2.00");
    // A day with only the simple estimate, and a day where the AI fell back: neither is AI.
    forecast(
        aqueduct,
        tank,
        "naive-persistence",
        false,
        "SHADOW_BASELINE",
        today.minusDays(5),
        "2.00",
        "2.00",
        "2.00");
    reading(aqueduct, tank, "2.20", today.minusDays(4));
    forecast(
        aqueduct,
        tank,
        "naive-persistence",
        true,
        "IA_ERROR",
        today.minusDays(4),
        "1.00",
        "1.50",
        "2.00");
    forecast(
        aqueduct,
        tank,
        "naive-persistence",
        false,
        "SHADOW_BASELINE",
        today.minusDays(4),
        "2.00",
        "2.00",
        "2.00");
    reading(aqueduct, tank, "2.30", today.minusDays(3));
    Session team = login(account("evaluation.team", "PROJECT_TEAM", aqueduct));

    mvc.perform(
            get("/api/v1/forecast-evaluation")
                .param("horizon_days", "1")
                .param("from", today.minusDays(10).toString())
                .param("to", today.toString())
                .header("Authorization", team.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.horizon_days").value(1))
        .andExpect(jsonPath("$.n").value(3))
        .andExpect(jsonPath("$.mae_ia").value(0.1167))
        .andExpect(jsonPath("$.mae_simple").value(0.5333))
        .andExpect(jsonPath("$.coverage_80").value(1.0))
        .andExpect(jsonPath("$.skill").value(greaterThan(0.0)))
        .andExpect(jsonPath("$.helps").value(nullValue()))
        .andExpect(jsonPath("$.verdict").value("Todavía no hay evidencia suficiente."))
        .andExpect(jsonPath("$.is_simulated").value(false));
  }

  @Test
  void anOperatorCannotSeeTheEvaluation() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    Session operator = login(account("evaluation.operator", "OPERATOR", aqueduct));

    mvc.perform(get("/api/v1/forecast-evaluation").header("Authorization", operator.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aBadHorizonOrAnInvertedPeriodIsRefused() throws Exception {
    UUID aqueduct = newAqueductWithRules();
    LocalDate today = LocalDate.now(ZONE);
    Session team = login(account("evaluation.refused", "PROJECT_TEAM", aqueduct));

    mvc.perform(
            get("/api/v1/forecast-evaluation")
                .param("horizon_days", "4")
                .header("Authorization", team.bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/forecast-evaluation")
                .param("from", today.toString())
                .param("to", today.minusDays(3).toString())
                .header("Authorization", team.bearer()))
        .andExpect(status().isBadRequest());
  }
}
