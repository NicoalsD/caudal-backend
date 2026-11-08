package co.caudal.infrastructure.persistence;

import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.LiveValveView;
import co.caudal.application.autopilot.ValveRow;
import co.caudal.application.port.out.AutopilotContextPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the live state of the valves, the shifts around now and the open anomalies. Row level
 * security limits the aqueduct.
 */
@Component
public class AutopilotContextPersistenceAdapter implements AutopilotContextPort {

  /**
   * The state of a valve is where its last confirmed command left it; the origin of a command is
   * its only non-null origin column.
   */
  private static final String VALVES =
      "SELECT v.id AS valve_id, v.sector_id, v.is_motorized, v.is_active,"
          + " CASE acked.command WHEN 'OPEN' THEN 'OPEN' WHEN 'CLOSE' THEN 'CLOSED'"
          + " ELSE 'UNKNOWN' END AS state,"
          + " last.id AS command_id, last.command AS command_action,"
          + " last.status AS command_status, last.origin AS command_origin,"
          + " last.created_at AS command_created_at,"
          + " COALESCE(ev.at, last.created_at) AS command_updated_at"
          + " FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id"
          + " LEFT JOIN LATERAL (SELECT vc.id, vc.command, vc.status, vc.created_at,"
          + " CASE WHEN vc.autopilot_decision_id IS NOT NULL THEN 'AUTOPILOT'"
          + " WHEN vc.manual_reason IS NOT NULL THEN 'MANUAL' ELSE 'SCHEDULE' END AS origin"
          + " FROM devices.valve_commands vc WHERE vc.valve_id = v.id"
          + " ORDER BY vc.created_at DESC, vc.id DESC LIMIT 1) last ON true"
          + " LEFT JOIN LATERAL (SELECT max(e.occurred_at) AS at"
          + " FROM devices.valve_command_events e WHERE e.command_id = last.id) ev ON true"
          + " LEFT JOIN LATERAL (SELECT vc.command FROM devices.valve_commands vc"
          + " JOIN devices.valve_command_events e ON e.command_id = vc.id AND e.event = 'ACKED'"
          + " WHERE vc.valve_id = v.id ORDER BY e.occurred_at DESC LIMIT 1) acked ON true"
          + " ORDER BY s.code, v.code, v.id";

  private static final String SHIFTS =
      "SELECT si.sector_id, si.start_at, si.end_at,"
          + " CASE ex.status WHEN 'COMPLETED' THEN 'DONE' WHEN 'PARTIAL' THEN 'PARTIAL'"
          + " WHEN 'NOT_EXECUTED' THEN 'SKIPPED' ELSE 'PLANNED' END AS status"
          + " FROM ops.schedule_items si JOIN ops.schedule_proposals sp"
          + " ON sp.id = si.proposal_id AND sp.aqueduct_id = si.aqueduct_id"
          + " LEFT JOIN LATERAL (SELECT e.status FROM ops.shift_executions e"
          + " WHERE e.schedule_item_id = si.id ORDER BY e.recorded_at DESC LIMIT 1) ex ON true"
          + " WHERE sp.status IN ('PUBLISHED', 'CLOSED') AND si.start_at < :to"
          + " AND si.end_at > :from ORDER BY si.start_at, si.sequence";

  /** Severity of each kind of anomaly, as the assistant expects it. */
  private static final Map<String, String> SEVERITY =
      Map.of(
          "POSSIBLE_LEAK", "HIGH",
          "ABNORMAL_DROP", "HIGH",
          "SENSOR_FAULT", "MEDIUM",
          "STALE_DATA", "MEDIUM",
          "SUSPECTED_DUPLICATE", "LOW");

  private static final String DEFAULT_SEVERITY = "LOW";

  private final JdbcClient jdbc;

  AutopilotContextPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ValveRow> valves() {
    return jdbc.sql(VALVES).query(AutopilotContextPersistenceAdapter::valve).list();
  }

  @Override
  public List<InsightsSnapshot.Shift> shifts(Instant from, Instant to) {
    return jdbc.sql(SHIFTS)
        .param("from", SqlTime.timestamp(from))
        .param("to", SqlTime.timestamp(to))
        .query(
            (rs, row) ->
                new InsightsSnapshot.Shift(
                    rs.getObject("sector_id", UUID.class),
                    SqlTime.instant(rs, "start_at"),
                    SqlTime.instant(rs, "end_at"),
                    rs.getString("status")))
        .list();
  }

  @Override
  public List<InsightsSnapshot.Anomaly> openAnomalies() {
    return jdbc.sql(
            "SELECT kind FROM ops.anomalies WHERE status = 'OPEN' ORDER BY detected_at DESC")
        .query(
            (rs, row) ->
                new InsightsSnapshot.Anomaly(
                    rs.getString("kind"),
                    SEVERITY.getOrDefault(rs.getString("kind"), DEFAULT_SEVERITY)))
        .list();
  }

  private static ValveRow valve(ResultSet rs, int row) throws SQLException {
    UUID commandId = rs.getObject("command_id", UUID.class);
    LiveValveView.LastCommand last =
        commandId == null
            ? null
            : new LiveValveView.LastCommand(
                commandId,
                rs.getString("command_action"),
                rs.getString("command_status"),
                rs.getString("command_origin"),
                SqlTime.instant(rs, "command_created_at"),
                SqlTime.instant(rs, "command_updated_at"));
    return new ValveRow(
        new LiveValveView(
            rs.getObject("valve_id", UUID.class),
            rs.getObject("sector_id", UUID.class),
            rs.getString("state"),
            last),
        rs.getBoolean("is_motorized"),
        rs.getBoolean("is_active"));
  }
}
