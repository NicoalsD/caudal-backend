package co.caudal.application.reading;

import java.util.Map;

/**
 * A problem found by the validation of a reading.
 *
 * @param code DUPLICATE or SUDDEN_JUMP
 * @param severity INFO or WARNING
 * @param details parameters of the problem
 */
public record IssueView(String code, String severity, Map<String, Object> details) {

  /** Code of a repeated reading. */
  public static final String DUPLICATE = "DUPLICATE";

  /** Code of a jump in the level that is too fast. */
  public static final String SUDDEN_JUMP = "SUDDEN_JUMP";

  /** Severity of a warning. */
  public static final String WARNING = "WARNING";

  /** Copies the details so the record is immutable. */
  public IssueView {
    details = Map.copyOf(details);
  }
}
