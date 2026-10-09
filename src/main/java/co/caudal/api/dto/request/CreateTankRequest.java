package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Data to create a tank.
 *
 * @param name name
 * @param gaugeMin minimum of the gauge
 * @param gaugeMax maximum of the gauge
 * @param gaugeStep reading precision
 * @param capacityLiters capacity, optional
 */
@Schema(description = "Alta de un tanque con el rango de su regla pintada.")
public record CreateTankRequest(
    @Schema(description = "Nombre: de 2 a 60 caracteres.", example = "Tanque principal")
        @NotNull
        @Size(min = FieldLimits.PLACE_NAME_MIN, max = FieldLimits.PLACE_NAME_MAX)
        @Pattern(regexp = FieldLimits.PLACE_NAME_PATTERN)
        String name,
    @Schema(description = "Valor mínimo de la regla.", example = "0.00")
        @NotNull
        @Digits(integer = 4, fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal gaugeMin,
    @Schema(description = "Valor máximo de la regla; mayor que el mínimo.", example = "5.00")
        @NotNull
        @Digits(integer = 4, fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal gaugeMax,
    @Schema(description = "Precisión de lectura; mayor que 0.", example = "0.05")
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 2, fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal gaugeStep,
    @Schema(description = "Capacidad en litros (opcional).", example = "20000") @Positive
        Integer capacityLiters) {}
