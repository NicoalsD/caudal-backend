package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Why the Board issues a reset code.
 *
 * @param reason 10 to 500 characters, checked by the use case
 */
@Schema(description = "Motivo del código de restablecimiento.")
public record IssueResetGrantRequest(
    @Schema(
            description = "Motivo, de 10 a 500 caracteres. Si falta responde 422 REASON_REQUIRED.",
            example = "La persona olvidó su contraseña y lo pidió en la reunión.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String reason) {}
