package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The approval of a proposal.
 *
 * @param lockVersion the version the member saw
 */
@Schema(description = "Aprobación de una propuesta tal como está.")
public record ApproveProposalRequest(
    @Schema(description = "Versión que vio el miembro de la Junta.", example = "0") @NotNull @Min(0)
        Integer lockVersion) {}
