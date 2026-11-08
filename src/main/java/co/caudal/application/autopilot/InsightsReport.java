package co.caudal.application.autopilot;

import java.time.Instant;
import java.util.List;

/**
 * The narrative report of a period.
 *
 * @param source {@code LLM} or {@code RULES}
 * @param model the model or the version of the rules
 * @param generatedAt when it was produced
 * @param title title
 * @param summary short summary
 * @param findings findings of the period
 * @param recommendations recommendations for the board
 * @param markdown the whole report as Markdown
 */
public record InsightsReport(
    String source,
    String model,
    Instant generatedAt,
    String title,
    String summary,
    List<String> findings,
    List<String> recommendations,
    String markdown) {

  /** Freezes the lists. */
  public InsightsReport {
    findings = List.copyOf(findings);
    recommendations = List.copyOf(recommendations);
  }
}
