package co.caudal.infrastructure.persistence;

import co.caudal.application.device.DeviceView;
import co.caudal.application.port.out.DevicePort;
import co.caudal.domain.device.DeviceKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes field devices. Row level security limits the aqueduct. */
@Component
public class DevicePersistenceAdapter implements DevicePort {

  private static final String COLUMNS =
      "id, aqueduct_id, kind, name, tank_id, valve_id, status, firmware_version, last_seen_at";

  private final JdbcClient jdbc;

  DevicePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DeviceView> device(UUID id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM devices.devices WHERE id = :id")
        .param("id", id)
        .query(DevicePersistenceAdapter::device)
        .optional();
  }

  @Override
  public DeviceView insertDevice(
      UUID aqueductId,
      DeviceKind kind,
      String name,
      UUID tankId,
      UUID valveId,
      String firmwareVersion,
      UUID createdBy) {
    return jdbc.sql(
            "INSERT INTO devices.devices (aqueduct_id, kind, name, tank_id, valve_id,"
                + " firmware_version, created_by) VALUES (:aqueduct, :kind, :name, :tank, :valve,"
                + " :firmware, :createdBy) RETURNING "
                + COLUMNS)
        .param("aqueduct", aqueductId)
        .param("kind", kind.name())
        .param("name", name)
        .param("tank", tankId, Types.OTHER)
        .param("valve", valveId, Types.OTHER)
        .param("firmware", firmwareVersion, Types.VARCHAR)
        .param("createdBy", createdBy)
        .query(DevicePersistenceAdapter::device)
        .single();
  }

  @Override
  public DeviceView updateStatus(UUID id, String status) {
    return jdbc.sql(
            "UPDATE devices.devices SET status = :status WHERE id = :id RETURNING " + COLUMNS)
        .param("status", status)
        .param("id", id)
        .query(DevicePersistenceAdapter::device)
        .single();
  }

  private static DeviceView device(ResultSet rs, int row) throws SQLException {
    return new DeviceView(
        rs.getObject("id", UUID.class),
        rs.getObject("aqueduct_id", UUID.class),
        rs.getString("kind"),
        rs.getString("name"),
        rs.getObject("tank_id", UUID.class),
        rs.getObject("valve_id", UUID.class),
        rs.getString("status"),
        rs.getString("firmware_version"),
        SqlTime.instant(rs, "last_seen_at"));
  }
}
