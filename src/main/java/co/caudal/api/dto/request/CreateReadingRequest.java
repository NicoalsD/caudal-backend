package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data of a reading of the tank.
 *
 * @param id id chosen by the client (idempotency key)
 * @param tankId the tank
 * @param gaugeValue value read on the gauge
 * @param waterAppearanceCode appearance of the water
 * @param damageNoticed true if damage was noticed
 * @param note free note
 * @param observedAt moment of the observation, ISO-8601 with zone
 */
@Schema(description = "Lectura del nivel del tanque.")
public record CreateReadingRequest(
    @Schema(description = "UUID generado por el cliente; sirve de llave de idempotencia.") @NotNull
        UUID id,
    @Schema(description = "Tanque del acueducto.") @NotNull UUID tankId,
    @Schema(
            description = "Valor leído, con máximo 2 decimales y dentro de la regla.",
            example = "2.10")
        @NotNull
        BigDecimal gaugeValue,
    @Schema(description = "Código del catálogo WATER_APPEARANCE.", example = "NORMAL")
        @NotNull
        @Size(min = FieldLimits.CATALOG_CODE_MIN, max = FieldLimits.CATALOG_CODE_MAX)
        @Pattern(regexp = FieldLimits.CATALOG_CODE_PATTERN)
        String waterAppearanceCode,
    @Schema(description = "Se notó algún daño. Por defecto falso.", example = "false")
        Boolean damageNoticed,
    @Schema(description = "Nota libre de hasta 500 caracteres y 10 líneas.", nullable = true)
        @Size(max = FieldLimits.NOTE_MAX)
        String note,
    @Schema(
            description =
                "Momento de la observación, ISO-8601 con zona. No más de 5 minutos en el futuro ni"
                    + " más viejo que max_backdate_days.",
            example = "2026-10-08T18:00:00-05:00")
        OffsetDateTime observedAt) {}
