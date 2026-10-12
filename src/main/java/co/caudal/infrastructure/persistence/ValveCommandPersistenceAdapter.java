package co.caudal.infrastructure.persistence;

import co.caudal.application.device.command.ValveCommandView;
import co.caudal.application.port.out.ValveCommandPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
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
