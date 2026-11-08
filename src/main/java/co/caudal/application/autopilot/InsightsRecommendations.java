package co.caudal.application.autopilot;

import java.time.Instant;
import java.util.List;

/**
 * The answer of {@code POST /v1/insights/recommendations}, or the one the rules produce when the
 * service cannot answer.
 *
 * @param source {@code LLM} or {@code RULES}
 * @param model the model or the version of the rules
 * @param generatedAt when it was produced
 * @param summary short text in Spanish
 * @param recommendations the recommendations, at most five
 */
public record InsightsRecommendations(
    String source,
    String model,
    Instant generatedAt,
    String summary,
    List<InsightRecommendation> recommendations) {

  /** Source of an answer from the language model. */
  public static final String SOURCE_LLM = "LLM";

  /** Source of an answer from the deterministic rules. */
  public static final String SOURCE_RULES = "RULES";

  /** Freezes the list. */
  public InsightsRecommendations {
    recommendations = List.copyOf(recommendations);
  }
}
