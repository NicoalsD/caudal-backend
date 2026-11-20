package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A role to grant to an existing account.
 *
 * @param userId the account
 * @param aqueductId the aqueduct; must be the one of the session
 * @param roleCode the role
 * @param validFrom start of the validity; now if missing
 * @param validTo end of the validity; open if missing
 */
@Schema(description = "Membresía nueva de una cuenta existente en el acueducto de la sesión.")
public record GrantMembershipRequest(
    @Schema(description = "Cuenta.", example = "0190f3a2-0000-7000-8000-000000000002") @NotNull
        UUID userId,
    @Schema(
            description = "Acueducto; debe ser el de la sesión.",
            example = "0190f3a2-0000-7000-8000-0000000000aa")
        @NotNull
        UUID aqueductId,
    @Schema(
            description =
                "Rol: INSTITUTION_ADMIN, OPERATOR o VIEWER (los roles de la Junta, BOARD_ADMIN, BOARD_MEMBER, PROJECT_TEAM y SUPPORT_ENTITY, siguen existiendo para las membresías heredadas).",
            example = "BOARD_MEMBER")
        @NotNull
        @Size(min = FieldLimits.CATALOG_CODE_MIN, max = FieldLimits.CATALOG_CODE_MAX)
        @Pattern(regexp = FieldLimits.CATALOG_CODE_PATTERN)
        String roleCode,
    @Schema(description = "Inicio de la vigencia; por defecto, ahora.", nullable = true)
        Instant validFrom,
    @Schema(description = "Fin de la vigencia; mayor que valid_from.", nullable = true)
        Instant validTo) {}
