package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.ReportPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Computes the numbers of a period from readings, shifts, valve commands, autopilot decisions,
 * incidents and anomalies. Row level security limits the aqueduct; no personal data is read.
 */
@Component
public class ReportPersistenceAdapter implements ReportPort {

  private static final String LEVELS =
      "SELECT count(*) AS readings, min(gauge_value) AS min_level, max(gauge_value) AS max_level,"
          + " round(avg(gauge_value), 2) AS avg_level FROM ops.readings"
          + " WHERE observed_at >= :from AND observed_at < :to";

  private static final String SHIFTS =
      "SELECT s.name AS sector, count(*) AS planned,"
          + " round(sum(extract(epoch FROM si.end_at - si.start_at)) / 3600, 2) AS planned_hours,"
          + " count(*) FILTER (WHERE ex.status = 'COMPLETED') AS completed,"
          + " count(*) FILTER (WHERE ex.status = 'PARTIAL') AS partial,"
          + " count(*) FILTER (WHERE ex.status = 'NOT_EXECUTED') AS not_executed"
          + " FROM ops.schedule_items si JOIN ops.schedule_proposals sp"
          + " ON sp.id = si.proposal_id AND sp.aqueduct_id = si.aqueduct_id"
          + " JOIN org.sectors s ON s.id = si.sector_id"
          + " LEFT JOIN LATERAL (SELECT e.status FROM ops.shift_executions e"
          + " WHERE e.schedule_item_id = si.id ORDER BY e.recorded_at DESC LIMIT 1) ex ON true"
          + " WHERE sp.status IN ('PUBLISHED', 'CLOSED') AND si.start_at >= :from"
          + " AND si.start_at < :to GROUP BY s.name ORDER BY s.name";

  private static final String COMMANDS =
      "SELECT CASE WHEN autopilot_decision_id IS NOT NULL THEN 'AUTOPILOT'"
          + " WHEN manual_reason IS NOT NULL THEN 'MANUAL' ELSE 'SCHEDULE' END AS origin,"
          + " status, count(*) AS total FROM devices.valve_commands"
          + " WHERE created_at >= :from AND created_at < :to GROUP BY 1, 2 ORDER BY 1, 2";

  private static final String DECISIONS =
      "SELECT source, action, status, count(*) AS total FROM ops.autopilot_decisions"
          + " WHERE created_at >= :from AND created_at < :to GROUP BY 1, 2, 3 ORDER BY 1, 2, 3";

  private static final String INCIDENTS =
      "SELECT category_code AS category, status, count(*) AS total FROM ops.incidents"
          + " WHERE reported_at >= :from AND reported_at < :to GROUP BY 1, 2 ORDER BY 1, 2";

  private static final String ANOMALIES =
      "SELECT kind, count(*) AS total FROM ops.anomalies"
          + " WHERE detected_at >= :from AND detected_at < :to GROUP BY 1 ORDER BY 1";

  private final JdbcClient jdbc;

  ReportPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<String, Object> aggregates(Instant from, Instant to) {
    Timestamp start = SqlTime.timestamp(from);
    Timestamp end = SqlTime.timestamp(to);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("levels", rows(LEVELS, start, end).getFirst());
    result.put("shifts_by_sector", rows(SHIFTS, start, end));
    result.put("valve_commands", rows(COMMANDS, start, end));
    result.put("autopilot_decisions", rows(DECISIONS, start, end));
    result.put("incidents", rows(INCIDENTS, start, end));
    result.put("anomalies", rows(ANOMALIES, start, end));
    return result;
  }

  private List<Map<String, Object>> rows(String sql, Timestamp from, Timestamp to) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> row :
        jdbc.sql(sql).param("from", from).param("to", to).query().listOfRows()) {
      rows.add(new LinkedHashMap<>(row));
    }
    return rows;
  }
}
