package co.caudal.domain.autopilot;

import java.time.Duration;

/**
 * Any human action pauses the autopilot: it only moves a valve once {@code
 * operator_absence_minutes} have passed since the last manual command, acceptance or rejection.
 *
 * @pattern P19 Strategy
 */
public final class HumanPriorityGuard implements SafetyGuard {

  /** Code written as the detail of a decision this guard held back. */
  public static final String CODE = "OPERATOR_ACTIVE";

  @Override
  public boolean allows(AutopilotAction action, CycleContext context) {
    if (context.lastHumanActionAt() == null) {
      return true;
    }
    Duration absence = Duration.ofMinutes(context.operatorAbsenceMinutes());
    return !context.lastHumanActionAt().plus(absence).isAfter(context.now());
  }

  @Override
  public String code() {
    return CODE;
  }
}
