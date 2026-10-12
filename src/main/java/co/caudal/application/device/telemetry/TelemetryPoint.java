package co.caudal.application.device.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One point of the telemetry of a device, as the device sent it (docs/Protocolo-de-dispositivos.md,
 * section 4.1).
 *
 * @param id the id chosen by the device, the key of idempotency
 * @param metric LEVEL, BATTERY_V, RSSI or VALVE_POSITION
 * @param value the measured value
 * @param observedAt the moment of the measurement, at microsecond precision
 */
public record TelemetryPoint(UUID id, String metric, BigDecimal value, Instant observedAt) {

  /** Validates the parts that every point has. */
  public TelemetryPoint {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(observedAt, "observedAt");
  }
}
