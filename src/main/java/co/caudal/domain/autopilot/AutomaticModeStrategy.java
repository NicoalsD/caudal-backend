package co.caudal.domain.autopilot;

import java.util.List;

/**
 * {@code AUTOMATIC}: an OPEN or CLOSE is executed when the operator has been absent for the
 * configured time. If a person acted recently, or the action does not move a valve, the decision
 * stays pending.
 *
 * @pattern P19 Strategy
 */
public final class AutomaticModeStrategy implements ModeStrategy {

  private final List<SafetyGuard> guards;
  private final SafetyGuard humanPriority;

  /**
   * Creates the strategy.
   *
   * @param guards the guards that apply in every mode
   * @param humanPriority the guard that pauses the autopilot after a human action
   */
  public AutomaticModeStrategy(List<SafetyGuard> guards, SafetyGuard humanPriority) {
    this.guards = List.copyOf(guards);
    this.humanPriority = humanPriority;
  }

  @Override
  public AutopilotMode mode() {
    return AutopilotMode.AUTOMATIC;
  }

  @Override
  public Resolution resolve(Recommendation recommendation, CycleContext context) {
    return Strategies.refusedBy(guards, recommendation, context)
        .map(Resolution::skipped)
        .orElseGet(() -> afterGuards(recommendation, context));
  }

  private Resolution afterGuards(Recommendation recommendation, CycleContext context) {
    if (!recommendation.action().movesValve()) {
      return Resolution.pending(null);
    }
    if (!humanPriority.allows(recommendation.action(), context)) {
      return Resolution.pending(humanPriority.code());
    }
    return Resolution.execute();
  }
}
