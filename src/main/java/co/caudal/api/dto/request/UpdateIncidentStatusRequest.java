package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A change of status of an incident. The note is checked by the use case, so that a dismissal
 * without a reason answers 422 REASON_REQUIRED.
 *
 * @param status VERIFYING, CONFIRMED, RESOLVED or DISMISSED
 * @param note the note of the change, optional
 */
@Schema(description = "Cambio de estado de un incidente.")
public record UpdateIncidentStatusRequest(
    @Schema(description = "Estado nuevo.", example = "CONFIRMED")
        @NotNull
        @Pattern(regexp = FieldLimits.INCIDENT_CHANGE_PATTERN)
        String status,
    @Schema(
            description =
                "Nota de hasta 500 caracteres. Para descartar es obligatoria, de 10 a 500.",
            nullable = true,
            example = "Se verificó en campo el 9 de octubre.")
        @Size(max = FieldLimits.NOTE_MAX)
        String note) {}
