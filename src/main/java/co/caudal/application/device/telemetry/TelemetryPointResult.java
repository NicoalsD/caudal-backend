package co.caudal.application.device.telemetry;

import java.util.UUID;

/**
 * What happened to one point of a batch.
 *
 * @param id the point
 * @param status ACCEPTED, DUPLICATE or REJECTED
 * @param readingId the reading created or already created from the point, or null
 * @param validationStatus ACCEPTED or FLAGGED for a reading, or null
 * @param errorCode the error code of a rejected point, or null
 */
public record TelemetryPointResult(
    UUID id, String status, UUID readingId, String validationStatus, String errorCode) {

  /** Status of a stored point. */
  public static final String ACCEPTED = "ACCEPTED";

  /** Status of a point that was already stored with the same content. */
  public static final String DUPLICATE = "DUPLICATE";

  /** Status of a point that was not stored. */
  public static final String REJECTED = "REJECTED";

  /**
   * A stored point.
   *
   * @param id the point
   * @param readingId the reading created, or null
   * @param validationStatus the validation of the reading, or null
   * @return the result
   */
  public static TelemetryPointResult accepted(UUID id, UUID readingId, String validationStatus) {
    return new TelemetryPointResult(id, ACCEPTED, readingId, validationStatus, null);
  }

  /**
   * A point that was already stored.
   *
   * @param id the point
   * @param readingId the reading it created, or null
   * @return the result
   */
  public static TelemetryPointResult duplicate(UUID id, UUID readingId) {
    return new TelemetryPointResult(id, DUPLICATE, readingId, null, null);
  }

  /**
   * A point that was not stored.
   *
   * @param id the point
   * @param errorCode the error code
   * @return the result
   */
  public static TelemetryPointResult rejected(UUID id, String errorCode) {
    return new TelemetryPointResult(id, REJECTED, null, null, errorCode);
  }
}
