package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A membership.
 *
 * @param id the membership
 * @param userId the account
 * @param aqueductId the aqueduct
 * @param roleCode the role
 * @param validFrom start of the validity
 * @param validTo end of the validity
 * @param grantedBy who granted it
 */
@Schema(description = "Membresía de una cuenta en un acueducto con un rol y una vigencia.")
public record MembershipResponse(
    @Schema(description = "Identificador.") UUID id,
    @Schema(description = "Cuenta.") UUID userId,
    @Schema(description = "Acueducto.") UUID aqueductId,
    @Schema(description = "Rol.", example = "OPERATOR") String roleCode,
    @Schema(description = "Inicio de la vigencia.") Instant validFrom,
    @Schema(description = "Fin de la vigencia; nulo mientras esté abierta.", nullable = true)
        Instant validTo,
    @Schema(description = "Quién la otorgó.") UUID grantedBy) {}
