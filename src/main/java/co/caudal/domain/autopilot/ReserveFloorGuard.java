package co.caudal.domain.autopilot;

/**
 * Never opens a valve when the tank is at or under the reserve floor. An unknown level counts as
 * under the floor: without a reading the autopilot cannot prove there is water to spare. CLOSE,
 * WAIT and INSPECT always pass.
 *
 * @pattern P19 Strategy
 */
public final class ReserveFloorGuard implements SafetyGuard {

  /** Code written as the detail of a decision this guard refused. */
  public static final String CODE = "RESERVE_FLOOR";

  @Override
  public boolean allows(AutopilotAction action, CycleContext context) {
    if (action != AutopilotAction.OPEN) {
      return true;
    }
    return context.levelPct() != null
        && context.levelPct().compareTo(context.reserveFloorPct()) > 0;
  }

  @Override
  public String code() {
    return CODE;
  }
}
