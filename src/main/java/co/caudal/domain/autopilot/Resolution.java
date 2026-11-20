package co.caudal.domain.autopilot;

/**
 * What the mode decides to do with a fresh recommendation.
 *
 * @param outcome what happens to the decision
 * @param detail code of the guard that changed the course, or null
 */
public record Resolution(Outcome outcome, String detail) {

  /** What happens to a fresh decision. */
  public enum Outcome {
    /** It is stored as pending. */
    PENDING,
    /** The autopilot creates the command now and marks the decision executed. */
    EXECUTE,
    /** A guard refused it. */
    SKIPPED
  }

  /**
   * A decision that waits.
   *
   * @param detail why it waits, or null
   * @return the resolution
   */
  public static Resolution pending(String detail) {
    return new Resolution(Outcome.PENDING, detail);
  }

  /**
   * A decision the autopilot executes.
   *
   * @return the resolution
   */
  public static Resolution execute() {
    return new Resolution(Outcome.EXECUTE, null);
  }

  /**
   * A decision a guard refused.
   *
   * @param detail the code of the guard
   * @return the resolution
   */
  public static Resolution skipped(String detail) {
    return new Resolution(Outcome.SKIPPED, detail);
  }
}
