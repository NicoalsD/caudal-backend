package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.EvaluationPort;
import co.caudal.domain.evaluation.EvaluationSample;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Pairs each forecast of the AI with the simple estimate stored beside it (same tank, same last
 * datum, {@code SHADOW_BASELINE}) and with the last effective reading of the day forecast. Runs
 * that replaced the AI with the simple estimate are not evaluated as AI.
 */
@Component
public class EvaluationPersistenceAdapter implements EvaluationPort {

  private static final String SAMPLES =
      "WITH points AS (SELECT r.tank_id, r.is_fallback, r.fallback_reason, r.context_to,"
          + " p.target_date, p.p10, p.p50, p.p90, p.target_date - (r.context_to AT TIME ZONE"
          + " :zone)::date AS horizon FROM ops.forecast_runs r JOIN ops.forecast_points p ON"
          + " p.forecast_run_id = r.id WHERE p.target_date BETWEEN :from AND :to)"
          + " SELECT ia.target_date, ia.p10, ia.p50, ia.p90, s.p50 AS simple_p50, a.gauge_value"
          + " FROM points ia JOIN points s ON s.tank_id = ia.tank_id AND s.context_to ="
          + " ia.context_to AND s.target_date = ia.target_date AND s.fallback_reason ="
          + " 'SHADOW_BASELINE' JOIN LATERAL (SELECT e.gauge_value FROM ops.effective_readings e"
          + " WHERE e.tank_id = ia.tank_id AND (e.observed_at AT TIME ZONE :zone)::date ="
          + " ia.target_date ORDER BY e.observed_at DESC LIMIT 1) a ON true"
          + " WHERE NOT ia.is_fallback AND ia.fallback_reason IS NULL AND ia.horizon = :horizon"
          + " ORDER BY ia.target_date";

  private final JdbcClient jdbc;

  EvaluationPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<EvaluationSample> samples(
      int horizonDays, LocalDate from, LocalDate to, ZoneId zone) {
    return jdbc.sql(SAMPLES)
        .param("zone", zone.getId())
        .param("from", from)
        .param("to", to)
        .param("horizon", horizonDays)
        .query(
            (rs, row) ->
                new EvaluationSample(
                    rs.getObject("target_date", LocalDate.class),
                    rs.getBigDecimal("gauge_value"),
                    rs.getBigDecimal("p10"),
                    rs.getBigDecimal("p50"),
                    rs.getBigDecimal("p90"),
                    rs.getBigDecimal("simple_p50")))
        .list();
  }
}
