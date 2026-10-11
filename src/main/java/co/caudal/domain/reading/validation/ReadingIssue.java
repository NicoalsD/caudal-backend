package co.caudal.domain.reading.validation;

import java.util.Map;

/**
 * A warning about a reading that is stored anyway, marked {@code FLAGGED}.
 *
 * @param code {@code DUPLICATE} or {@code SUDDEN_JUMP}
 * @param severity {@code INFO} or {@code WARNING}
 * @param details the values that explain it
 */
public record ReadingIssue(String code, String severity, Map<String, Object> details) {

  /** A repeated value of the same tank inside the window. */
  public static final String DUPLICATE = "DUPLICATE";

  /** The level moved faster than the rules allow. */
  public static final String SUDDEN_JUMP = "SUDDEN_JUMP";

  /** Severity of a warning that needs no action. */
  public static final String INFO = "INFO";

  /** Severity of a warning a person should review. */
  public static final String WARNING = "WARNING";

  /** Freezes the details. */
  public ReadingIssue {
    details = Map.copyOf(details);
  }
}
