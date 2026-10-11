package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Changes of a sector; every field is optional. The code never changes.
 *
 * @param name new name
 * @param householdsCount new number of households
 * @param isActive new state
 */
@Schema(description = "Cambios de un sector. El código no cambia.")
public record UpdateSectorRequest(
    @Schema(description = "Nombre: 2 a 60 caracteres.", nullable = true)
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(description = "Viviendas que atiende.", nullable = true) @PositiveOrZero
        Integer householdsCount,
    @Schema(description = "Si sigue en uso.", nullable = true) Boolean isActive) {}
