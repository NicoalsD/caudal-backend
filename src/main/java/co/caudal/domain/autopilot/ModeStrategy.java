package co.caudal.domain.autopilot;

import java.util.List;

/**
 * Decides what to do with a fresh recommendation according to the mode. The reserve floor applies
 * in every mode; only {@code AUTOMATIC} may execute, and only when no person acted recently.
 *
 * @pattern P19 Strategy
 */
public interface ModeStrategy {

  /**
   * The mode this strategy serves.
   *
   * @return the mode
   */
  AutopilotMode mode();

  /**
   * Resolves a recommendation.
   *
   * @param recommendation the recommendation
   * @param context the state of the aqueduct
   * @return what to do with it
   */
  Resolution resolve(Recommendation recommendation, CycleContext context);

  /**
   * The strategy of a mode.
   *
   * @param mode the mode
   * @return its strategy
   */
  static ModeStrategy of(AutopilotMode mode) {
    List<SafetyGuard> reserve = List.of(new ReserveFloorGuard());
    return switch (mode) {
      case MANUAL -> new ManualModeStrategy(reserve);
      case ASSISTED -> new AssistedModeStrategy(reserve);
      case AUTOMATIC -> new AutomaticModeStrategy(reserve, new HumanPriorityGuard());
    };
  }
}
