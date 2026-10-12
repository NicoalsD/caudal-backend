package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An authorization for an entity. The use case checks the reason (422 REASON_REQUIRED), the period,
 * the expiry and the role of the grantee.
 *
 * @param granteeUserId the user of the entity
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day
 * @param periodTo last day
 * @param reason why, 10 to 500 characters
 * @param expiresAt when it stops working
 */
@Schema(description = "Autorización de la Junta para que una entidad vea resúmenes.")
public record CreateSummaryShareRequest(
    @Schema(description = "Usuario con rol SUPPORT_ENTITY del acueducto.") @NotNull
        UUID granteeUserId,
    @Schema(description = "MONTHLY_SUMMARY o MINUTES.", example = "MONTHLY_SUMMARY")
        @NotNull
        @Pattern(regexp = FieldLimits.SUMMARY_SCOPE_PATTERN)
        String scope,
    @Schema(description = "Primer día del periodo.", example = "2026-10-01") @NotNull
        LocalDate periodFrom,
    @Schema(description = "Último día del periodo.", example = "2026-10-31") @NotNull
        LocalDate periodTo,
    @Schema(
            description = "Motivo, de 10 a 500 caracteres. Si falta responde 422 REASON_REQUIRED.",
            example = "Informe para la alcaldía municipal.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String reason,
    @Schema(description = "Vencimiento; debe ser futuro.", example = "2026-11-30T23:59:00-05:00")
        @NotNull
        Instant expiresAt) {}
