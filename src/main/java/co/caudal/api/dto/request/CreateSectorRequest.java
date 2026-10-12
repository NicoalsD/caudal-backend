package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A new sector.
 *
 * @param code short code, unique in the aqueduct
 * @param name name
 * @param parentSectorId the sector it belongs to
 * @param householdsCount households it serves
 */
@Schema(description = "Sector nuevo del acueducto de la sesión.")
public record CreateSectorRequest(
    @Schema(description = "Código: 2 a 20 de A-Z, 0-9 y guion; único.", example = "SEC-01")
        @NotNull
        @Size(min = FieldLimits.SECTOR_CODE_MIN, max = FieldLimits.SECTOR_CODE_MAX)
        @Pattern(regexp = FieldLimits.SECTOR_CODE_PATTERN)
        String code,
    @Schema(description = "Nombre: 2 a 60 caracteres.", example = "Sector Ejemplo")
        @NotNull
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(description = "Sector al que pertenece.", nullable = true) UUID parentSectorId,
    @Schema(description = "Viviendas que atiende; 0 si no llega.", example = "12", nullable = true)
        @PositiveOrZero
        Integer householdsCount) {}
