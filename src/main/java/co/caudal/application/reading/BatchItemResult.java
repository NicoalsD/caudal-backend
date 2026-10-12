package co.caudal.application.reading;

import java.util.Map;
import java.util.UUID;

/**
 * What happened to one reading of a batch.
 *
 * @param id the id of the reading
 * @param status {@code ACCEPTED}, {@code FLAGGED}, {@code REPLAYED} or {@code REJECTED}
 * @param errorCode why it was refused, or null
 * @param errorDetails the values that explain the refusal, or an empty map
 */
public record BatchItemResult(
    UUID id, String status, String errorCode, Map<String, Object> errorDetails) {

  /** A reading that was refused and not stored. */
  public static final String REJECTED = "REJECTED";

  /** A retry of a reading already stored. */
  public static final String REPLAYED = "REPLAYED";

  /** Freezes the details. */
  public BatchItemResult {
    errorDetails = errorDetails == null ? Map.of() : Map.copyOf(errorDetails);
  }
}
