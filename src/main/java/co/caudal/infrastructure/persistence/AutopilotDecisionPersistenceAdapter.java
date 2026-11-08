package co.caudal.infrastructure.persistence;

import co.caudal.application.autopilot.DecisionView;
import co.caudal.application.autopilot.NewDecision;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.domain.autopilot.DecisionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes {@code ops.autopilot_decisions}. Row level security limits the aqueduct. */
@Component
public class AutopilotDecisionPersistenceAdapter implements AutopilotDecisionPort {

  private static final String SELECT =
      "SELECT d.id, d.created_at, d.source, d.model, d.action, d.valve_id, d.reason,"
          + " d.confidence, d.status, d.status_detail, d.decided_by, d.decided_at,"
          + " (SELECT vc.id FROM devices.valve_commands vc WHERE vc.autopilot_decision_id = d.id"
          + " ORDER BY vc.created_at DESC, vc.id DESC LIMIT 1) AS valve_command_id"
          + " FROM ops.autopilot_decisions d";

  /** Statuses that stamp {@code decided_at}: somebody or something resolved the decision. */
  private static final Set<DecisionStatus> RESOLVED =
      Set.of(DecisionStatus.ACCEPTED, DecisionStatus.REJECTED, DecisionStatus.EXECUTED);

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AutopilotDecisionPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public DecisionView insert(NewDecision decision) {
    UUID id =
        jdbc.sql(
                "INSERT INTO ops.autopilot_decisions (aqueduct_id, source, model, action,"
                    + " valve_id, reason, confidence, status, status_detail, inputs) VALUES"
                    + " (:aqueduct, :source, :model, :action, :valve, :reason, :confidence,"
                    + " :status, :detail, CAST(:inputs AS jsonb)) RETURNING id")
            .param("aqueduct", decision.aqueductId())
            .param("source", decision.source())
            .param("model", decision.model())
            .param("action", decision.action())
            .param("valve", decision.valveId(), Types.OTHER)
            .param("reason", decision.reason())
            .param("confidence", decision.confidence())
            .param("status", decision.status())
            .param("detail", decision.statusDetail(), Types.VARCHAR)
            .param("inputs", json.writeValueAsString(decision.inputs()))
            .query(UUID.class)
            .single();
    return find(id).orElseThrow();
  }

  @Override
  public int expirePending() {
    return jdbc.sql(
            "UPDATE ops.autopilot_decisions SET status = 'EXPIRED' WHERE status = 'PENDING'")
        .update();
  }

  @Override
  public List<DecisionView> list(DecisionStatus status, int limit) {
    String where = status == null ? "" : " WHERE d.status = :status";
    var statement = jdbc.sql(SELECT + where + " ORDER BY d.created_at DESC, d.id ASC LIMIT :limit");
    if (status != null) {
      statement = statement.param("status", status.name());
    }
    return statement
        .param("limit", limit)
        .query(AutopilotDecisionPersistenceAdapter::decision)
        .list();
  }

  @Override
  public Optional<DecisionView> find(UUID id) {
    return jdbc.sql(SELECT + " WHERE d.id = :id")
        .param("id", id)
        .query(AutopilotDecisionPersistenceAdapter::decision)
        .optional();
  }

  @Override
  public boolean transition(
      UUID id, DecisionStatus from, DecisionStatus to, UUID decidedBy, String detail, Instant at) {
    return jdbc.sql(
                "UPDATE ops.autopilot_decisions SET status = :to, decided_by = :by,"
                    + " decided_at = :at, status_detail = :detail"
                    + " WHERE id = :id AND status = :from")
            .param("to", to.name())
            .param("by", decidedBy, Types.OTHER)
            .param(
                "at",
                RESOLVED.contains(to) ? SqlTime.offset(at) : null,
                Types.TIMESTAMP_WITH_TIMEZONE)
            .param("detail", detail, Types.VARCHAR)
            .param("id", id)
            .param("from", from.name())
            .update()
        > 0;
  }

  @Override
  public Optional<Instant> lastHumanActionAt() {
    // The row is always there; its value is null when nobody has acted, hence the Optional.
    return jdbc.sql(
            "SELECT GREATEST("
                + " (SELECT max(created_at) FROM devices.valve_commands"
                + " WHERE manual_reason IS NOT NULL),"
                + " (SELECT max(decided_at) FROM ops.autopilot_decisions"
                + " WHERE decided_by IS NOT NULL)) AS last_action")
        .query((rs, row) -> Optional.ofNullable(SqlTime.instant(rs, "last_action")))
        .single();
  }

  @Override
  public void markExecutedByCommand(UUID commandId) {
    jdbc.sql(
            "UPDATE ops.autopilot_decisions SET status = 'EXECUTED' WHERE status = 'ACCEPTED'"
                + " AND id = (SELECT autopilot_decision_id FROM devices.valve_commands"
                + " WHERE id = :command)")
        .param("command", commandId)
        .update();
  }

  private static DecisionView decision(ResultSet rs, int row) throws SQLException {
    return new DecisionView(
        rs.getObject("id", UUID.class),
        SqlTime.instant(rs, "created_at"),
        rs.getString("source"),
        rs.getString("model"),
        rs.getString("action"),
        rs.getObject("valve_id", UUID.class),
        rs.getString("reason"),
        rs.getBigDecimal("confidence"),
        rs.getString("status"),
        rs.getString("status_detail"),
        rs.getObject("decided_by", UUID.class),
        SqlTime.instant(rs, "decided_at"),
        rs.getObject("valve_command_id", UUID.class));
  }
}
