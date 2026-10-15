package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Validity of the key of a device. The key material is never shown.
 *
 * @param id key id
 * @param validFrom when the key was registered
 * @param validUntil when the key stops being accepted, or null if it is open-ended
 */
@Schema(description = "Clave pública vigente del dispositivo; sin el material de la clave.")
public record DeviceKeyValidityResponse(
    @Schema(description = "Identificador de la clave.") UUID id,
    @Schema(description = "Momento desde el que la clave se acepta.") Instant validFrom,
    @Schema(
            description = "Momento en que deja de aceptarse; nulo si no tiene fin.",
            nullable = true)
        Instant validUntil) {}
