package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A correction of a reading.
 *
 * @param correctedGaugeValue the right value, on the gauge of the tank
 * @param reason why, 10 to 500 characters, checked by the use case
 */
@Schema(description = "Corrección de una lectura; el dato original no cambia.")
public record CorrectReadingRequest(
    @Schema(description = "Valor corregido, dentro de la regla del tanque.", example = "2.00")
        @NotNull
        @Digits(
            integer = FieldLimits.GAUGE_INTEGER_DIGITS_MAX,
            fraction = FieldLimits.GAUGE_DECIMALS_MAX)
        BigDecimal correctedGaugeValue,
    @Schema(
            description = "Motivo, de 10 a 500 caracteres. Si falta responde 422 REASON_REQUIRED.",
            example = "El fontanero leyó mal la regla; se confirmó con la foto.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String reason) {}
