package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Start of a new version of the rules.
 *
 * @param changeReason why the rules change; optional in a draft, required to activate
 */
@Schema(description = "Borrador nuevo copiado de la versión activa.")
public record CreateRuleSetDraftRequest(
    @Schema(
            description = "Motivo del cambio, de 10 a 500 caracteres; se exige al activar.",
            example = "Sube la reserva de 1,00 a 1,20 por la temporada seca.",
            nullable = true)
        @Size(min = FieldLimits.REASON_MIN, max = FieldLimits.REASON_MAX)
        String changeReason) {}
