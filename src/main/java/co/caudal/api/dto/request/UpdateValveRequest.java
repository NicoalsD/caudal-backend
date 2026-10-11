package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Changes of a valve; every field is optional. The code never changes.
 *
 * @param name new name
 * @param locationHint new hint; an empty text clears it
 * @param isMotorized new motorized flag
 * @param failSafePosition new fail-safe position
 * @param isActive new state
 */
@Schema(description = "Cambios de una válvula. El código no cambia.")
public record UpdateValveRequest(
    @Schema(description = "Nombre: 2 a 60 caracteres.", nullable = true)
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(description = "Dónde está; vacío la borra.", nullable = true)
        @Size(max = FieldLimits.LOCATION_HINT_MAX)
        @Pattern(regexp = FieldLimits.SINGLE_LINE_PATTERN)
        String locationHint,
    @Schema(description = "Si la mueve un dispositivo.", nullable = true) Boolean isMotorized,
    @Schema(description = "KEEP, OPEN o CLOSED.", nullable = true)
        @Pattern(regexp = FieldLimits.FAIL_SAFE_POSITION_PATTERN)
        String failSafePosition,
    @Schema(description = "Si sigue en uso.", nullable = true) Boolean isActive) {}
