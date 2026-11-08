package co.caudal.domain.autopilot;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One recommendation, from the assistant or from the rules.
 *
 * @param action what to do
 * @param valveId the valve, null when the action does not move one
 * @param reason why, in Spanish
 * @param confidence from 0 to 1
 */
public record Recommendation(
    AutopilotAction action, UUID valveId, String reason, BigDecimal confidence) {

  /** Checks that a valve movement names its valve. */
  public Recommendation {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(confidence, "confidence");
    if (action.movesValve() && valveId == null) {
      throw new IllegalArgumentException("A valve movement needs a valve");
    }
  }
}
