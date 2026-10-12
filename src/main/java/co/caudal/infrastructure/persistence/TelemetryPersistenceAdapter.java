package co.caudal.infrastructure.persistence;

import co.caudal.application.device.telemetry.StoredTelemetryPoint;
import co.caudal.application.device.telemetry.TelemetryPoint;
import co.caudal.application.port.out.TelemetryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes the telemetry points of the devices. Row level security limits the aqueduct. */
@Component
public class TelemetryPersistenceAdapter implements TelemetryPort {

  private final JdbcClient jdbc;

  TelemetryPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<StoredTelemetryPoint> find(UUID id) {
    return jdbc.sql(
            "SELECT id, device_id, metric, value, observed_at, reading_id"
                + " FROM devices.telemetry_points WHERE id = :id")
        .param("id", id)
        .query(TelemetryPersistenceAdapter::stored)
        .optional();
  }

  @Override
  public boolean insert(UUID aqueductId, UUID deviceId, TelemetryPoint point, UUID readingId) {
    return jdbc.sql(
                "INSERT INTO devices.telemetry_points (id, aqueduct_id, device_id, metric, value,"
                    + " observed_at, reading_id) VALUES (:id, :aqueduct, :device, :metric, :value,"
                    + " :observed, :reading) ON CONFLICT DO NOTHING")
            .param("id", point.id())
            .param("aqueduct", aqueductId)
            .param("device", deviceId)
            .param("metric", point.metric())
            .param("value", point.value())
            .param("observed", SqlTime.timestamp(point.observedAt()))
            .param("reading", readingId, Types.OTHER)
            .update()
        > 0;
  }

  private static StoredTelemetryPoint stored(ResultSet rs, int row) throws SQLException {
    return new StoredTelemetryPoint(
        rs.getObject("id", UUID.class),
        rs.getObject("device_id", UUID.class),
        rs.getString("metric"),
        rs.getBigDecimal("value"),
        SqlTime.instant(rs, "observed_at"),
        rs.getObject("reading_id", UUID.class));
  }
}
