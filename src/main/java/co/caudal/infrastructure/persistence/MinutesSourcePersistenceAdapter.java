package co.caudal.infrastructure.persistence;

import co.caudal.application.minutes.MinutesFact;
import co.caudal.application.port.out.MinutesSourcePort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads every record of a period that the minutes tell. Only values go out, never names, phones or
 * the descriptions people typed. Row level security limits the aqueduct.
 */
@Component
public class MinutesSourcePersistenceAdapter implements MinutesSourcePort {

  private static final int LEVEL_SCALE = 2;
  private static final String FALLBACK = " (estimación simple)";

  private final JdbcClient jdbc;

  MinutesSourcePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<MinutesFact> facts(Instant from, Instant to) {
    List<MinutesFact> facts = new ArrayList<>();
    facts.addAll(
        read(
            "SELECT id, observed_at AS at, validation_status AS status, gauge_value"
                + " FROM ops.readings WHERE observed_at >= :from AND observed_at < :to",
            from,
            to,
            (rs, f) ->
                fact(rs, "reading", Map.of("valor", level(rs.getBigDecimal("gauge_value"))))));
    facts.addAll(
        read(
            "SELECT id, created_at AS at, NULL AS status, corrected_gauge_value, reason"
                + " FROM ops.reading_corrections WHERE created_at >= :from AND created_at < :to",
            from,
            to,
            (rs, f) ->
                fact(
                    rs,
                    "correction",
                    Map.of(
                        "valor", level(rs.getBigDecimal("corrected_gauge_value")),
                        "motivo", rs.getString("reason")))));
    facts.addAll(
        read(
            "SELECT i.id, i.reported_at AS at, i.status, COALESCE(c.label_es, i.category_code) AS"
                + " category FROM ops.incidents i LEFT JOIN org.catalog_items c ON c.catalog ="
                + " 'DAMAGE_CATEGORY' AND c.code = i.category_code AND (c.aqueduct_id IS NULL OR"
                + " c.aqueduct_id = i.aqueduct_id) WHERE i.reported_at >= :from AND i.reported_at"
                + " < :to",
            from,
            to,
            (rs, f) -> fact(rs, "incident", Map.of("categoria", rs.getString("category")))));
    facts.addAll(
        read(
            "SELECT id, created_at AS at, NULL AS status, model_name, horizon_days, is_fallback"
                + " FROM ops.forecast_runs WHERE created_at >= :from AND created_at < :to",
            from,
            to,
            (rs, f) ->
                fact(
                    rs,
                    "forecast",
                    Map.of(
                        "modelo", rs.getString("model_name"),
                        "dias", Integer.toString(rs.getInt("horizon_days")),
                        "respaldo", rs.getBoolean("is_fallback") ? FALLBACK : ""))));
    facts.addAll(proposalsAndAnomalies(from, to));
    facts.addAll(executions(from, to));
    return facts;
  }

  private List<MinutesFact> proposalsAndAnomalies(Instant from, Instant to) {
    List<MinutesFact> facts = new ArrayList<>();
    facts.addAll(
        read(
            "SELECT id, generated_at AS at, status, service_date, available_hours"
                + " FROM ops.schedule_proposals WHERE generated_at >= :from AND generated_at < :to",
            from,
            to,
            (rs, f) ->
                fact(
                    rs,
                    "proposal",
                    Map.of(
                        "dia", rs.getObject("service_date", LocalDate.class).toString(),
                        "horas", level(rs.getBigDecimal("available_hours"))))));
    facts.addAll(
        read(
            "SELECT a.id, a.detected_at AS at, a.status, a.kind, i.status AS incident_status"
                + " FROM ops.anomalies a LEFT JOIN ops.incidents i ON i.id = a.related_incident_id"
                + " WHERE a.detected_at >= :from AND a.detected_at < :to",
            from,
            to,
            (rs, f) -> {
              Map<String, String> values = new HashMap<>();
              values.put("tipo", rs.getString("kind"));
              if (rs.getString("incident_status") != null) {
                values.put("incidente", rs.getString("incident_status"));
              }
              return fact(rs, "anomaly", values);
            }));
    return facts;
  }

  private List<MinutesFact> executions(Instant from, Instant to) {
    return read(
        "SELECT e.id, e.recorded_at AS at, e.status, s.name AS sector, p.service_date"
            + " FROM ops.shift_executions e JOIN ops.schedule_items i ON i.id = e.schedule_item_id"
            + " JOIN ops.schedule_proposals p ON p.id = i.proposal_id JOIN org.sectors s ON s.id ="
            + " i.sector_id WHERE e.recorded_at >= :from AND e.recorded_at < :to AND NOT EXISTS"
            + " (SELECT 1 FROM ops.shift_executions x WHERE x.supersedes_id = e.id)",
        from,
        to,
        (rs, f) ->
            fact(
                rs,
                "execution",
                Map.of(
                    "sector", rs.getString("sector"),
                    "dia", rs.getObject("service_date", LocalDate.class).toString())));
  }

  private List<MinutesFact> read(
      String sql,
      Instant from,
      Instant to,
      org.springframework.jdbc.core.RowMapper<MinutesFact> map) {
    return jdbc.sql(sql)
        .param("from", SqlTime.offset(from), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(to), Types.TIMESTAMP_WITH_TIMEZONE)
        .query(map)
        .list();
  }

  private static MinutesFact fact(ResultSet rs, String source, Map<String, String> values)
      throws SQLException {
    return new MinutesFact(
        source, rs.getString("id"), SqlTime.instant(rs, "at"), rs.getString("status"), values);
  }

  private static String level(BigDecimal value) {
    return value.setScale(LEVEL_SCALE, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
  }
}
