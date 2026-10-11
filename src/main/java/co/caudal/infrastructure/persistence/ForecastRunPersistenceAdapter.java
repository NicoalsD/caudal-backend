package co.caudal.infrastructure.persistence;

import co.caudal.application.forecast.ForecastPoint;
import co.caudal.application.forecast.ForecastRunRecord;
import co.caudal.application.port.out.ForecastRunPort;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Writes {@code ops.forecast_runs} and {@code ops.forecast_points}. RLS limits the aqueduct. */
@Component
public class ForecastRunPersistenceAdapter implements ForecastRunPort {

  private final JdbcClient jdbc;

  ForecastRunPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UUID insert(ForecastRunRecord run) {
    UUID id =
        jdbc.sql(
                "INSERT INTO ops.forecast_runs (aqueduct_id, tank_id, model_name, model_version,"
                    + " is_fallback, fallback_reason, horizon_days, context_from, context_to,"
                    + " input_points, latency_ms, rule_set_id, requested_by) VALUES (:aqueduct,"
                    + " :tank, :model, :version, :fallback, :reason, :horizon, :from, :to,"
                    + " :points, :latency, :ruleSet, :by) RETURNING id")
            .param("aqueduct", run.aqueductId())
            .param("tank", run.tankId())
            .param("model", run.forecast().modelName())
            .param("version", run.forecast().modelVersion())
            .param("fallback", run.fallback())
            .param("reason", run.fallbackReason(), Types.VARCHAR)
            .param("horizon", run.horizonDays())
            .param("from", SqlTime.offset(run.contextFrom()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("to", SqlTime.offset(run.contextTo()), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("points", run.inputPoints())
            .param("latency", run.forecast().latencyMs())
            .param("ruleSet", run.ruleSetId())
            .param("by", run.requestedBy(), Types.OTHER)
            .query(UUID.class)
            .single();
    for (ForecastPoint point : run.forecast().points()) {
      jdbc.sql(
              "INSERT INTO ops.forecast_points (forecast_run_id, target_date, aqueduct_id, p10,"
                  + " p50, p90) VALUES (:run, :day, :aqueduct, :p10, :p50, :p90)")
          .param("run", id)
          .param("day", point.targetDate())
          .param("aqueduct", run.aqueductId())
          .param("p10", point.p10())
          .param("p50", point.p50())
          .param("p90", point.p90())
          .update();
    }
    return id;
  }
}
