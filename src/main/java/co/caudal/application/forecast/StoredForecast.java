package co.caudal.application.forecast;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A stored forecast run as people see it (docs/API.md, section 8.2).
 *
 * @param runId the run
 * @param modelName the model
 * @param modelVersion its version
 * @param fallback true when the simple estimate replaced the service
 * @param fallbackReason why, or null
 * @param horizonDays days forecast
 * @param createdAt when it was made
 * @param contextFrom first moment of the history used
 * @param contextTo last moment of the history used
 * @param points one point per day
 */
public record StoredForecast(
    UUID runId,
    String modelName,
    String modelVersion,
    boolean fallback,
    String fallbackReason,
    int horizonDays,
    Instant createdAt,
    Instant contextFrom,
    Instant contextTo,
    List<ForecastPoint> points) {

  /** Copies the points. */
  public StoredForecast {
    points = List.copyOf(points);
  }
}
