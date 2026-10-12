package co.caudal.infrastructure.persistence;

import co.caudal.application.forecast.ForecastPoint;
import co.caudal.application.forecast.ForecastRunRecord;
import co.caudal.application.forecast.StoredForecast;
import co.caudal.application.port.out.ForecastRunPort;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

  @Override
  public Optional<StoredForecast> latestPrimary(UUID tankId) {
    return jdbc.sql(
            "SELECT id, model_name, model_version, is_fallback, fallback_reason, horizon_days,"
                + " created_at, context_from, context_to FROM ops.forecast_runs"
                + " WHERE tank_id = :tank AND fallback_reason IS DISTINCT FROM 'SHADOW_BASELINE'"
                + " ORDER BY created_at DESC, id DESC LIMIT 1")
        .param("tank", tankId)
        .query(
            (rs, row) -> {
              UUID id = rs.getObject("id", UUID.class);
              return new StoredForecast(
                  id,
                  rs.getString("model_name"),
                  rs.getString("model_version"),
                  rs.getBoolean("is_fallback"),
                  rs.getString("fallback_reason"),
                  rs.getInt("horizon_days"),
                  SqlTime.instant(rs, "created_at"),
                  SqlTime.instant(rs, "context_from"),
                  SqlTime.instant(rs, "context_to"),
                  List.of());
            })
        .optional()
        .map(
            run ->
                new StoredForecast(
                    run.runId(),
                    run.modelName(),
                    run.modelVersion(),
                    run.fallback(),
                    run.fallbackReason(),
                    run.horizonDays(),
                    run.createdAt(),
                    run.contextFrom(),
                    run.contextTo(),
                    points(run.runId())));
  }

  private List<ForecastPoint> points(UUID runId) {
    return jdbc.sql(
            "SELECT target_date, p10, p50, p90 FROM ops.forecast_points"
                + " WHERE forecast_run_id = :run ORDER BY target_date")
        .param("run", runId)
        .query(
            (rs, row) ->
                new ForecastPoint(
                    rs.getObject("target_date", LocalDate.class),
                    rs.getBigDecimal("p10"),
                    rs.getBigDecimal("p50"),
                    rs.getBigDecimal("p90")))
        .list();
  }
}
