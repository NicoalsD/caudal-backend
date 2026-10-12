package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The resolution of an anomaly.
 *
 * @param status CONFIRMED or DISMISSED
 * @param resolutionNote the note; 10 to 500 characters to dismiss
 * @param relatedIncidentId the incident it led to
 */
@Schema(description = "Confirmación o descarte de una anomalía.")
public record ReviewAnomalyRequest(
    @Schema(description = "CONFIRMED o DISMISSED.", example = "DISMISSED")
        @NotNull
        @Pattern(regexp = FieldLimits.ANOMALY_RESOLUTION_PATTERN)
        String status,
    @Schema(
            description = "Nota; obligatoria con 10 a 500 caracteres para descartar.",
            nullable = true)
        @Size(max = FieldLimits.NOTE_MAX)
        String resolutionNote,
    @Schema(description = "Incidente relacionado.", nullable = true) UUID relatedIncidentId) {}
