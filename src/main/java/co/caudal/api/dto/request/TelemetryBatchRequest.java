package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shape of a telemetry batch (docs/API.md, section 16.1). The controller receives the raw bytes,
 * because the signature covers them, and reads them with the adapter of the protocol; this record
 * only documents the body in Swagger.
 *
 * @param points one to one hundred points
 */
@Schema(description = "Lote de telemetría firmado de un dispositivo.")
public record TelemetryBatchRequest(List<TelemetryPointRequest> points) {

  /**
   * One point of a batch.
   *
   * @param id the id chosen by the device, the key of idempotency
   * @param metric LEVEL, BATTERY_V, RSSI or VALVE_POSITION
   * @param value the measured value, up to three decimals (two for LEVEL)
   * @param observedAt the moment of the measurement, ISO-8601 with zone
   */
  @Schema(description = "Punto de telemetría.")
  public record TelemetryPointRequest(
      UUID id, String metric, BigDecimal value, OffsetDateTime observedAt) {}
}
