package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * A public key of a device. The format is checked by the application, which answers 422
 * INVALID_KEY.
 *
 * @param publicKey the key in base64url without padding
 */
@Schema(description = "Clave pública Ed25519 del dispositivo.")
public record RegisterDeviceKeyRequest(
    @Schema(
            description =
                "Clave pública en base64url sin relleno: 43 caracteres que decodifican a 32 bytes.",
            example = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo")
        @NotNull
        String publicKey) {}
