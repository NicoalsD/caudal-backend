package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A damage report from the public page.
 *
 * @param categoryCode a code of DAMAGE_CATEGORY
 * @param sectorId the sector, or null
 * @param description what happened, 10 to 500
 * @param locationHint where, up to 120
 * @param website hidden field; people leave it empty
 * @param formStartedAt when the form was opened
 */
@Schema(description = "Reporte de daño desde la página pública, sin datos personales.")
public record DamageReportRequest(
    @Schema(description = "Código de DAMAGE_CATEGORY.", example = "LEAK")
        @NotNull
        @Size(min = FieldLimits.CATALOG_CODE_MIN, max = FieldLimits.CATALOG_CODE_MAX)
        @Pattern(regexp = FieldLimits.CATALOG_CODE_PATTERN)
        String categoryCode,
    @Schema(description = "Sector, si se sabe.", nullable = true) UUID sectorId,
    @Schema(
            description = "Qué pasó, 10 a 500 caracteres.",
            example = "Hay agua saliendo del tubo de la calle principal.")
        @NotNull
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String description,
    @Schema(description = "Dónde, hasta 120 caracteres.", example = "Esquina de la tienda")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String locationHint,
    @Schema(description = "Campo oculto; debe llegar vacío.", nullable = true)
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String website,
    @Schema(description = "Cuándo se abrió el formulario.", example = "2026-10-09T09:00:00-05:00")
        OffsetDateTime formStartedAt) {}
