package co.caudal.application.anomaly;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An anomaly (docs/API.md, section 9).
 *
 * @param id the anomaly
 * @param kind what was suspected
 * @param detectedAt when
 * @param status {@code OPEN}, {@code CONFIRMED} or {@code DISMISSED}
 * @param evidence the facts that raised it
 * @param resolvedBy who resolved it, or null
 * @param resolvedAt when, or null
 * @param resolutionNote the note, or null
 * @param relatedIncidentId the incident it led to, or null
 */
public record AnomalyView(
    UUID id,
    String kind,
    Instant detectedAt,
    String status,
    Map<String, Object> evidence,
    UUID resolvedBy,
    Instant resolvedAt,
    String resolutionNote,
    UUID relatedIncidentId) {

  /** Freezes the evidence. */
  public AnomalyView {
    evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
  }
}
