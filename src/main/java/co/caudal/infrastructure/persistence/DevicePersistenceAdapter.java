package co.caudal.infrastructure.persistence;

import co.caudal.application.device.DeviceKeyView;
import co.caudal.application.device.DeviceView;
import co.caudal.application.device.auth.DeviceCredentials;
import co.caudal.application.device.auth.DeviceKeyCredential;
import co.caudal.application.port.out.DevicePort;
import co.caudal.domain.device.DeviceKind;
import co.caudal.domain.security.SecurityEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
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
  public Optional<DeviceCredentials> credentials(UUID deviceId) {
    List<CredentialRow> rows =
        jdbc.sql(
                "SELECT aqueduct_id, status, kind, tank_id, valve_id, key_id, public_key"
                    + " FROM devices.find_device_credentials(:device)")
            .param("device", deviceId)
            .query(DevicePersistenceAdapter::credentialRow)
            .list();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    CredentialRow first = rows.getFirst();
    List<DeviceKeyCredential> keys =
        rows.stream()
            .filter(row -> row.keyId() != null)
            .map(row -> new DeviceKeyCredential(row.keyId(), row.publicKey()))
            .toList();
    return Optional.of(
        new DeviceCredentials(
            deviceId,
            first.aqueductId(),
            first.status(),
            first.kind(),
            first.tankId(),
            first.valveId(),
            keys));
  }

  @Override
  public long countSecurityEvents(UUID deviceId, SecurityEventType type, Instant since) {
    return jdbc.sql(
            "SELECT count(*) FROM iam.security_events WHERE device_id = :device AND type = :type"
                + " AND created_at >= :since")
        .param("device", deviceId)
        .param("type", type.name())
        .param("since", SqlTime.timestamp(since))
        .query(Long.class)
        .single();
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

  @Override
  public void retireActiveKey(UUID deviceId, int graceDays) {
    jdbc.sql(
            "UPDATE devices.device_keys SET revoked_at = now() + make_interval(days => :days)"
                + " WHERE device_id = :device AND revoked_at IS NULL")
        .param("days", graceDays)
        .param("device", deviceId)
        .update();
  }

  @Override
  public Optional<DeviceKeyView> insertKey(
      UUID deviceId, UUID aqueductId, String publicKey, String fingerprint) {
    return jdbc.sql(
            "INSERT INTO devices.device_keys (aqueduct_id, device_id, public_key, fingerprint)"
                + " VALUES (:aqueduct, :device, :key, :fingerprint) ON CONFLICT DO NOTHING"
                + " RETURNING id, fingerprint, activated_at")
        .param("aqueduct", aqueductId)
        .param("device", deviceId)
        .param("key", publicKey)
        .param("fingerprint", fingerprint)
        .query(DevicePersistenceAdapter::key)
        .optional();
  }

  @Override
  public Optional<DeviceKeyView> revokeKey(UUID deviceId, UUID keyId) {
    return jdbc.sql(
            "UPDATE devices.device_keys SET revoked_at = now()"
                + " WHERE id = :key AND device_id = :device"
                + " AND (revoked_at IS NULL OR revoked_at > now())"
                + " RETURNING id, fingerprint, activated_at")
        .param("key", keyId)
        .param("device", deviceId)
        .query(DevicePersistenceAdapter::key)
        .optional();
  }

  @Override
  public boolean hasKey(UUID deviceId, UUID keyId) {
    return jdbc.sql(
                "SELECT count(*) FROM devices.device_keys WHERE id = :key AND device_id = :device")
            .param("key", keyId)
            .param("device", deviceId)
            .query(Long.class)
            .single()
        > 0;
  }

  private static DeviceKeyView key(ResultSet rs, int row) throws SQLException {
    return new DeviceKeyView(
        rs.getObject("id", UUID.class),
        rs.getString("fingerprint"),
        SqlTime.instant(rs, "activated_at"));
  }

  private static CredentialRow credentialRow(ResultSet rs, int row) throws SQLException {
    return new CredentialRow(
        rs.getObject("aqueduct_id", UUID.class),
        rs.getString("status"),
        rs.getString("kind"),
        rs.getObject("tank_id", UUID.class),
        rs.getObject("valve_id", UUID.class),
        rs.getObject("key_id", UUID.class),
        rs.getString("public_key"));
  }

  /** One row of the credentials function: the device, and one of its valid keys if any. */
  private record CredentialRow(
      UUID aqueductId,
      String status,
      String kind,
      UUID tankId,
      UUID valveId,
      UUID keyId,
      String publicKey) {}

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
