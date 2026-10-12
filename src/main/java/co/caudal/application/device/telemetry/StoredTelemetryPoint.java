package co.caudal.application.device.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A telemetry point already stored, used to recognise a retry of the same point.
 *
 * @param id the point
 * @param deviceId the device that sent it
 * @param metric the metric
 * @param value the value
 * @param observedAt the moment of the measurement
 * @param readingId the reading created from it, or null
 */
public record StoredTelemetryPoint(
    UUID id, UUID deviceId, String metric, BigDecimal value, Instant observedAt, UUID readingId) {}
