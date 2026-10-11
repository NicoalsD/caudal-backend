package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new valve of a sector.
 *
 * @param code short code, unique in the sector
 * @param name name
 * @param locationHint where to find it
 * @param isMotorized whether a device can move it
 * @param failSafePosition position when control is lost
 */
@Schema(description = "Válvula nueva de un sector.")
public record CreateValveRequest(
    @Schema(
            description = "Código: 1 a 20 de A-Z, 0-9 y guion; único en el sector.",
            example = "V-01")
        @NotNull
        @Size(min = FieldLimits.VALVE_CODE_MIN, max = FieldLimits.VALVE_CODE_MAX)
        @Pattern(regexp = FieldLimits.VALVE_CODE_PATTERN)
        String code,
    @Schema(description = "Nombre: 2 a 60 caracteres.", example = "Válvula del puente")
        @NotNull
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(description = "Dónde está: hasta 120 caracteres en una línea.", nullable = true)
        @Size(max = FieldLimits.LOCATION_HINT_MAX)
        @Pattern(regexp = FieldLimits.SINGLE_LINE_PATTERN)
        String locationHint,
    @Schema(description = "Si la mueve un dispositivo; falso si no llega.", nullable = true)
        Boolean isMotorized,
    @Schema(description = "KEEP, OPEN o CLOSED; KEEP si no llega.", nullable = true)
        @Pattern(regexp = FieldLimits.FAIL_SAFE_POSITION_PATTERN)
        String failSafePosition) {}
