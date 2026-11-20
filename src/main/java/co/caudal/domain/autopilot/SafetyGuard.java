package co.caudal.domain.autopilot;

/**
 * A rule that decides whether the autopilot may carry out a recommendation. The autopilot has two:
 * the reserve floor ({@link ReserveFloorGuard}) and the priority of people ({@link
 * HumanPriorityGuard}).
 *
 * @pattern P19 Strategy
 */
public interface SafetyGuard {

  /**
   * Checks a recommendation against the state of the cycle.
   *
   * @param action what the recommendation proposes
   * @param context the state of the aqueduct
   * @return true if the recommendation may go on
   */
  boolean allows(AutopilotAction action, CycleContext context);

  /**
   * Short code of the rule, used as the detail of a skipped decision.
   *
   * @return the code
   */
  String code();
}
