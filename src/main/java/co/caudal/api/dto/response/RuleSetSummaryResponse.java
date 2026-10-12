package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One version of the rules in a list.
 *
 * @param id the version
 * @param version its number
 * @param status its status
 * @param validFrom start of its validity
 * @param validTo end of its validity
 * @param activatedAt when it was activated
 * @param basedOnRuleSetId the version it was copied from
 */
@Schema(description = "Versión de las reglas en un listado.")
public record RuleSetSummaryResponse(
    @Schema(description = "Identificador de la versión.") UUID id,
    @Schema(description = "Número de versión en el acueducto.", example = "3") int version,
    @Schema(description = "DRAFT, ACTIVE, SUPERSEDED o DISCARDED.", example = "ACTIVE")
        String status,
    @Schema(description = "Inicio de la vigencia; nulo en un borrador.", nullable = true)
        Instant validFrom,
    @Schema(description = "Fin de la vigencia; nulo mientras está activa.", nullable = true)
        Instant validTo,
    @Schema(description = "Momento de la activación.", nullable = true) Instant activatedAt,
    @Schema(description = "Versión de la que se copió.", nullable = true) UUID basedOnRuleSetId) {}
