package co.caudal.domain.execution;

import co.caudal.shared.error.FieldValidationException;
import java.time.Instant;

/**
 * The real hours of a shift (docs/Reglas-de-negocio.md, section 14): {@code COMPLETED} takes the
 * planned hours when none are given; {@code PARTIAL} needs the real start and takes the planned end
 * when none is given; {@code NOT_EXECUTED} has no hours. The end always comes after the start.
 *
 * @param start real start, or null
 * @param end real end, or null
 */
public record ExecutionTimes(Instant start, Instant end) {

  /** A shift that was not executed. */
  public static final String NOT_EXECUTED = "NOT_EXECUTED";

  /** A shift cut short. */
  public static final String PARTIAL = "PARTIAL";

  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  /**
   * Resolves the hours of an execution.
   *
   * @param status COMPLETED, PARTIAL or NOT_EXECUTED
   * @param start real start typed, or null
   * @param end real end typed, or null
   * @param plannedStart start of the shift
   * @param plannedEnd end of the shift
   * @return the hours to store
   * @throws FieldValidationException for hours that break the rules
   */
  public static ExecutionTimes resolve(
      String status, Instant start, Instant end, Instant plannedStart, Instant plannedEnd) {
    if (NOT_EXECUTED.equals(status)) {
      if (start != null) {
        throw new FieldValidationException("actual_start", NOT_ALLOWED);
      }
      if (end != null) {
        throw new FieldValidationException("actual_end", NOT_ALLOWED);
      }
      return new ExecutionTimes(null, null);
    }
    if (PARTIAL.equals(status) && start == null) {
      throw new FieldValidationException("actual_start", "FIELD_REQUIRED");
    }
    Instant realStart = start != null ? start : plannedStart;
    Instant realEnd = end != null ? end : plannedEnd;
    if (!realEnd.isAfter(realStart)) {
      throw new FieldValidationException("actual_end", NOT_ALLOWED);
    }
    return new ExecutionTimes(realStart, realEnd);
  }
}
