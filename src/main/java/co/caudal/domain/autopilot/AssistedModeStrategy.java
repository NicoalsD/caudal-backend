package co.caudal.domain.autopilot;

import java.util.List;

/**
 * {@code ASSISTED}: the decision waits for a person to accept or reject it.
 *
 * @pattern P19 Strategy
 */
public final class AssistedModeStrategy implements ModeStrategy {

  private final List<SafetyGuard> guards;

  /**
   * Creates the strategy.
   *
   * @param guards the guards that apply in every mode
   */
  public AssistedModeStrategy(List<SafetyGuard> guards) {
    this.guards = List.copyOf(guards);
  }

  @Override
  public AutopilotMode mode() {
    return AutopilotMode.ASSISTED;
  }

  @Override
  public Resolution resolve(Recommendation recommendation, CycleContext context) {
    return Strategies.refusedBy(guards, recommendation, context)
        .map(Resolution::skipped)
        .orElseGet(() -> Resolution.pending(null));
  }
}
