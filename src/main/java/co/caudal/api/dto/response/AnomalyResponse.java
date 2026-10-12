package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An anomaly.
 *
 * @param id the anomaly
 * @param kind what was suspected
 * @param detectedAt when
 * @param status its status
 * @param evidence the facts that raised it
 * @param resolvedBy who resolved it
 * @param resolvedAt when
 * @param resolutionNote the note
 * @param relatedIncidentId the incident it led to
 */
@Schema(description = "Anomalía: una sospecha (INFERRED) hasta que alguien la confirme.")
public record AnomalyResponse(
    UUID id,
    @Schema(example = "STALE_DATA") String kind,
    Instant detectedAt,
    @Schema(example = "OPEN") String status,
    Map<String, Object> evidence,
    @Schema(nullable = true) UUID resolvedBy,
    @Schema(nullable = true) Instant resolvedAt,
    @Schema(nullable = true) String resolutionNote,
    @Schema(nullable = true) UUID relatedIncidentId) {}
