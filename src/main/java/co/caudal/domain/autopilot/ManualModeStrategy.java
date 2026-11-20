package co.caudal.domain.autopilot;

import java.util.List;

/**
 * {@code MANUAL}: the decision is stored and only shown.
 *
 * @pattern P19 Strategy
 */
public final class ManualModeStrategy implements ModeStrategy {

  private final List<SafetyGuard> guards;

  /**
   * Creates the strategy.
   *
   * @param guards the guards that apply in every mode
   */
  public ManualModeStrategy(List<SafetyGuard> guards) {
    this.guards = List.copyOf(guards);
  }

  @Override
  public AutopilotMode mode() {
    return AutopilotMode.MANUAL;
  }

  @Override
  public Resolution resolve(Recommendation recommendation, CycleContext context) {
    return Strategies.refusedBy(guards, recommendation, context)
        .map(Resolution::skipped)
        .orElseGet(() -> Resolution.pending(null));
  }
}
