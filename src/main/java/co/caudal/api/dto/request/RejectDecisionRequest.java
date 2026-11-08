package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Why a decision is rejected.
 *
 * @param reason the reason, optional
 */
@Schema(description = "Motivo opcional del rechazo.")
public record RejectDecisionRequest(
    @Schema(description = "Motivo, hasta 200 caracteres.", nullable = true)
        @Size(max = FieldLimits.AUTOPILOT_DETAIL_MAX)
        String reason) {}
