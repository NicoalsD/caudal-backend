package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A sector. It has no personal data.
 *
 * @param id sector id
 * @param code short code
 * @param name name
 * @param parentSectorId parent sector
 * @param householdsCount number of households
 * @param isActive in use
 */
@Schema(description = "Sector que recibe agua por turnos. Sin datos personales.")
public record SectorResponse(
    @Schema(description = "Identificador del sector.") UUID id,
    @Schema(description = "Código corto.", example = "ALTO") String code,
    @Schema(description = "Nombre.", example = "El Alto") String name,
    @Schema(description = "Sector padre.", nullable = true) UUID parentSectorId,
    @Schema(description = "Número de hogares (agregado).", example = "18") int householdsCount,
    @Schema(description = "El sector está activo.", example = "true") boolean isActive) {}
