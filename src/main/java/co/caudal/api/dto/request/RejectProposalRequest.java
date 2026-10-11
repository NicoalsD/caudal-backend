package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The rejection of a proposal.
 *
 * @param lockVersion the version the member saw
 * @param reason why, 10 to 500 characters
 */
@Schema(description = "Rechazo de una propuesta con su motivo.")
public record RejectProposalRequest(
    @Schema(description = "Versión que vio el miembro de la Junta.", example = "0") @NotNull @Min(0)
        Integer lockVersion,
    @Schema(
            description = "Motivo del rechazo, de 10 a 500 caracteres.",
            example = "El nivel bajó en la tarde; se genera otra propuesta.")
        @Size(max = FieldLimits.REASON_MAX)
        String reason) {}
