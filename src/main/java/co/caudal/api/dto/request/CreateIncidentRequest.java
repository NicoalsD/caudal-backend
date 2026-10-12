package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * An incident registered from the panel. The lengths of the description (10 to 500) and of the
 * location (up to 120) are checked by the use case with {@code FieldLimits}.
 *
 * @param categoryCode a code of DAMAGE_CATEGORY
 * @param sectorId the sector, or null
 * @param description what happened
 * @param locationHint where, or null
 */
@Schema(description = "Daño registrado por una persona del acueducto desde el panel.")
public record CreateIncidentRequest(
    @Schema(description = "Código de DAMAGE_CATEGORY.", example = "LEAK")
        @NotNull
        @Size(min = FieldLimits.CATALOG_CODE_MIN, max = FieldLimits.CATALOG_CODE_MAX)
        @Pattern(regexp = FieldLimits.CATALOG_CODE_PATTERN)
        String categoryCode,
    @Schema(description = "Sector del acueducto, si se sabe.", nullable = true) UUID sectorId,
    @Schema(
            description = "Qué pasó, de 10 a 500 caracteres.",
            example = "Gotea el tubo junto a la escuela.")
        @NotNull
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String description,
    @Schema(
            description = "Dónde, hasta 120 caracteres.",
            nullable = true,
            example = "Frente a la escuela")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String locationHint) {}
