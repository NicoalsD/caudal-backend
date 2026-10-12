package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A registered public key.
 *
 * @param keyId key id
 * @param fingerprint SHA-256 of the key, in hexadecimal
 * @param activatedAt when it was registered
 */
@Schema(description = "Clave pública registrada.")
public record DeviceKeyResponse(
    @Schema(description = "Identificador de la clave.") UUID keyId,
    @Schema(description = "SHA-256 de la clave, en hexadecimal (64 caracteres).")
        String fingerprint,
    @Schema(description = "Activación de la clave.") Instant activatedAt) {}
