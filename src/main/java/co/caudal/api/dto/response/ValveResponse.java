package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A valve.
 *
 * @param id valve id
 * @param code code
 * @param name name
 * @param locationHint reference of location
 * @param isMotorized has an actuator
 * @param failSafePosition KEEP, OPEN or CLOSED
 * @param isActive in use
 */
@Schema(description = "Válvula física de un sector.")
public record ValveResponse(
    @Schema(description = "Identificador de la válvula.") UUID id,
    @Schema(description = "Código, único por sector.", example = "V-ALTO") String code,
    @Schema(description = "Nombre.", example = "Válvula El Alto") String name,
    @Schema(description = "Referencia de ubicación.", nullable = true) String locationHint,
    @Schema(description = "Tiene actuador.", example = "false") boolean isMotorized,
    @Schema(description = "Posición segura: KEEP, OPEN o CLOSED.", example = "KEEP")
        String failSafePosition,
    @Schema(description = "La válvula está activa.", example = "true") boolean isActive) {}
