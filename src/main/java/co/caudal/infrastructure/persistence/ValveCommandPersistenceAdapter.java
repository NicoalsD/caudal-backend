package co.caudal.infrastructure.persistence;

import co.caudal.application.device.command.NewValveCommand;
import co.caudal.application.device.command.ValveCommandView;
import co.caudal.application.port.out.ValveCommandPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the valve commands and their history. Row level security limits the aqueduct.
 */
@Component
public class ValveCommandPersistenceAdapter implements ValveCommandPort {

  private static final String COLUMNS =
      "id, aqueduct_id, device_id, valve_id, command, status, not_before, expires_at";

  private final JdbcClient jdbc;

  ValveCommandPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ValveCommandView> open(UUID deviceId) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM devices.valve_commands WHERE device_id = :device"
                + " AND status IN ('PENDING', 'DELIVERED') ORDER BY not_before, id")
        .param("device", deviceId)
        .query(ValveCommandPersistenceAdapter::command)
        .list();
  }

  @Override
  public Optional<ValveCommandView> find(UUID commandId, UUID deviceId) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM devices.valve_commands WHERE id = :id AND device_id = :device")
        .param("id", commandId)
        .param("device", deviceId)
        .query(ValveCommandPersistenceAdapter::command)
        .optional();
  }

  @Override
  public boolean changeStatus(UUID commandId, String from, String to) {
    return jdbc.sql(
                "UPDATE devices.valve_commands SET status = :to WHERE id = :id AND status = :from")
            .param("to", to)
            .param("id", commandId)
            .param("from", from)
            .update()
        > 0;
  }

  @Override
  public void insertEvent(
      UUID aqueductId, UUID commandId, String event, String detail, Instant occurredAt) {
    jdbc.sql(
            "INSERT INTO devices.valve_command_events (aqueduct_id, command_id, event, detail,"
                + " occurred_at) VALUES (:aqueduct, :command, :event, :detail, :at)")
        .param("aqueduct", aqueductId)
        .param("command", commandId)
        .param("event", event)
        .param("detail", detail, Types.VARCHAR)
        .param("at", SqlTime.timestamp(occurredAt))
        .update();
  }

  @Override
  public List<ValveCommandView> pendingOfDay(UUID aqueductId, LocalDate day, UUID proposalId) {
    return jdbc.sql(
            "SELECT vc.id, vc.aqueduct_id, vc.device_id, vc.valve_id, vc.command, vc.status,"
                + " vc.not_before, vc.expires_at FROM devices.valve_commands vc"
                + " JOIN ops.schedule_items si ON si.id = vc.schedule_item_id"
                + " AND si.aqueduct_id = vc.aqueduct_id"
                + " JOIN ops.schedule_proposals sp ON sp.id = si.proposal_id"
                + " WHERE vc.aqueduct_id = :aqueduct AND vc.status = 'PENDING'"
                + " AND sp.service_date = :day AND sp.id <> :proposal"
                + " ORDER BY vc.not_before, vc.id")
        .param("aqueduct", aqueductId)
        .param("day", day)
        .param("proposal", proposalId)
        .query(ValveCommandPersistenceAdapter::command)
        .list();
  }

  @Override
  public Optional<ValveCommandView> insert(NewValveCommand command) {
    return jdbc.sql(
            "INSERT INTO devices.valve_commands (aqueduct_id, valve_id, device_id, command,"
                + " schedule_item_id, manual_reason, created_by, not_before, expires_at)"
                + " VALUES (:aqueduct, :valve, :device, :command, :item, :reason, :creator,"
                + " :notBefore, :expiresAt)"
                + " ON CONFLICT (valve_id, not_before)"
                + " WHERE status IN ('PENDING', 'DELIVERED') DO NOTHING"
                + " RETURNING "
                + COLUMNS)
        .param("aqueduct", command.aqueductId())
        .param("valve", command.valveId())
        .param("device", command.deviceId())
        .param("command", command.command())
        .param("item", command.scheduleItemId(), Types.OTHER)
        .param("reason", command.manualReason(), Types.VARCHAR)
        .param("creator", command.createdBy(), Types.OTHER)
        .param("notBefore", SqlTime.timestamp(command.notBefore()))
        .param("expiresAt", SqlTime.timestamp(command.expiresAt()))
        .query(ValveCommandPersistenceAdapter::command)
        .optional();
  }

  private static ValveCommandView command(ResultSet rs, int row) throws SQLException {
    return new ValveCommandView(
        rs.getObject("id", UUID.class),
        rs.getObject("aqueduct_id", UUID.class),
        rs.getObject("device_id", UUID.class),
        rs.getObject("valve_id", UUID.class),
        rs.getString("command"),
        rs.getString("status"),
        SqlTime.instant(rs, "not_before"),
        SqlTime.instant(rs, "expires_at"));
  }
}
