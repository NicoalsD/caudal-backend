package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Changes of an account; every field is optional and a missing one keeps its value.
 *
 * @param fullName new full name
 * @param status new status
 */
@Schema(description = "Cambio del nombre o del estado de una cuenta. Los campos son opcionales.")
public record UpdateUserRequest(
    @Schema(
            description = "Nombre completo: de 2 a 80 letras, espacios, punto, apóstrofo y guion.",
            example = "Persona de Ejemplo",
            nullable = true)
        @Size(max = FieldLimits.PERSON_NAME_MAX * 2)
        String fullName,
    @Schema(
            description =
                "Estado: ACTIVE (desbloquea), LOCKED (bloquea hasta que se desbloquee) o DISABLED"
                    + " (desactiva para siempre). Bloquear o desactivar cierra todas sus sesiones.",
            example = "DISABLED",
            nullable = true)
        @Pattern(regexp = FieldLimits.ACCOUNT_STATUS_PATTERN)
        String status) {}
