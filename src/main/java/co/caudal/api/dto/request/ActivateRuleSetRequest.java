package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Activation of a draft.
 *
 * @param changeReason why the rules change; 10 to 500 characters, checked by the use case
 */
@Schema(description = "Activación de un borrador de reglas.")
public record ActivateRuleSetRequest(
    @Schema(
            description =
                "Motivo del cambio, de 10 a 500 caracteres. Si falta o no cumple, la API responde"
                    + " 422 REASON_REQUIRED.",
            example = "Se activa la reserva de 1,20 aprobada en la reunión de octubre.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String changeReason) {}
