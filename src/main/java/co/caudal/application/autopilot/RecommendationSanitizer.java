package co.caudal.application.autopilot;

import co.caudal.domain.autopilot.AutopilotAction;
import co.caudal.domain.autopilot.Recommendation;
import co.caudal.shared.FieldLimits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks the recommendations again on the backend, whoever produced them (docs/Contrato-IA.md,
 * insights section): an unknown action is dropped, a valve that is not in the snapshot is dropped,
 * an OPEN or CLOSE needs a motorized valve, the reason is cut to its limit, the confidence is held
 * between 0 and 1 and no more than five go on. The reserve floor is applied later, by the strategy
 * of the mode, so the skipped decision stays visible.
 */
public final class RecommendationSanitizer {

  private static final String ELLIPSIS = "...";
  private static final int CONFIDENCE_SCALE = 3;

  private RecommendationSanitizer() {}

  /**
   * Cleans the recommendations of an answer.
   *
   * @param answer the answer, from the model or from the rules
   * @param snapshot the snapshot that was sent
   * @return the valid recommendations, at most five
   */
  public static List<Recommendation> clean(
      InsightsRecommendations answer, InsightsSnapshot snapshot) {
    Map<UUID, InsightsSnapshot.Valve> valves =
        snapshot.valves().stream()
            .collect(
                Collectors.toMap(InsightsSnapshot.Valve::id, Function.identity(), (a, b) -> a));
    List<Recommendation> result = new ArrayList<>();
    for (InsightRecommendation raw : answer.recommendations()) {
      if (result.size() >= FieldLimits.INSIGHTS_RECOMMENDATIONS_MAX) {
        break;
      }
      Recommendation checked = check(raw, valves);
      if (checked != null) {
        result.add(checked);
      }
    }
    return result;
  }

  private static Recommendation check(
      InsightRecommendation raw, Map<UUID, InsightsSnapshot.Valve> valves) {
    AutopilotAction action = actionOf(raw.action());
    if (action == null
        || raw.reason() == null
        || raw.reason().isBlank()
        || raw.confidence() == null) {
      return null;
    }
    InsightsSnapshot.Valve valve = raw.valveId() == null ? null : valves.get(raw.valveId());
    if (raw.valveId() != null && valve == null) {
      return null;
    }
    if (action.movesValve() && (valve == null || !valve.motorized())) {
      return null;
    }
    return new Recommendation(
        action, raw.valveId(), cut(raw.reason().strip()), clamp(raw.confidence()));
  }

  private static AutopilotAction actionOf(String code) {
    for (AutopilotAction action : AutopilotAction.values()) {
      if (action.name().equals(code)) {
        return action;
      }
    }
    return null;
  }

  private static String cut(String reason) {
    int max = FieldLimits.AUTOPILOT_REASON_MAX;
    return reason.length() <= max
        ? reason
        : reason.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
  }

  private static BigDecimal clamp(BigDecimal confidence) {
    BigDecimal bounded = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    return bounded.setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP);
  }
}
