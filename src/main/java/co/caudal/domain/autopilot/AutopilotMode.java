package co.caudal.domain.autopilot;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Mode of the autopilot of an aqueduct. Each mode answers what the autopilot may do with a
 * recommendation, so the code that handles a recommendation never compares mode names:
 *
 * <ul>
 *   <li>{@link #MANUAL}: the recommendations are only shown;
 *   <li>{@link #ASSISTED}: a person accepts or rejects each one;
 *   <li>{@link #AUTOMATIC}: the autopilot executes an OPEN or CLOSE by itself when nobody has acted
 *       for {@code operator_absence_minutes}.
 * </ul>
 *
 * @pattern P18 State
 */
public enum AutopilotMode {
  /** Recommendations are shown, nothing else happens. */
  MANUAL(false, false),
  /** A person accepts or rejects each recommendation. */
  ASSISTED(true, false),
  /** The autopilot executes by itself while the operator is absent. */
  AUTOMATIC(true, true);

  private final boolean acceptsDecisions;
  private final boolean executesByItself;

  AutopilotMode(boolean acceptsDecisions, boolean executesByItself) {
    this.acceptsDecisions = acceptsDecisions;
    this.executesByItself = executesByItself;
  }

  /**
   * Restores the mode from its stored code.
   *
   * @param code {@code MANUAL}, {@code ASSISTED} or {@code AUTOMATIC}
   * @return the mode
   * @throws DomainException with {@code VALIDATION_ERROR} if the code is unknown
   */
  public static AutopilotMode of(String code) {
    if (code != null) {
      for (AutopilotMode mode : values()) {
        if (mode.name().equals(code)) {
          return mode;
        }
      }
    }
    throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "mode"));
  }

  /**
   * Whether a person can accept a pending decision in this mode (the buttons exist).
   *
   * @return true in {@code ASSISTED} and {@code AUTOMATIC}
   */
  public boolean acceptsDecisions() {
    return acceptsDecisions;
  }

  /**
   * Whether the autopilot may execute a valve movement without a person.
   *
   * @return true only in {@code AUTOMATIC}
   */
  public boolean executesByItself() {
    return executesByItself;
  }
}
