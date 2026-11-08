package co.caudal.domain.autopilot;

import java.util.List;
import java.util.Optional;

/** Helpers shared by the strategies of the modes. */
final class Strategies {

  private Strategies() {}

  /** The code of the first guard that refuses the recommendation, if any. */
  static Optional<String> refusedBy(
      List<SafetyGuard> guards, Recommendation recommendation, CycleContext context) {
    return guards.stream()
        .filter(guard -> !guard.allows(recommendation.action(), context))
        .map(SafetyGuard::code)
        .findFirst();
  }
}
