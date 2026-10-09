package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A tank and its painted gauge.
 *
 * @param id tank id
 * @param name name
 * @param gaugeMin minimum of the gauge
 * @param gaugeMax maximum of the gauge
 * @param gaugeStep reading precision
 * @param capacityLiters capacity
 * @param isActive in use
 */
@Schema(description = "Tanque y el rango de su regla pintada.")
public record TankResponse(
    @Schema(description = "Identificador del tanque.") UUID id,
    @Schema(description = "Nombre.", example = "Tanque principal") String name,
    @Schema(description = "Valor mínimo de la regla.", example = "0.00") BigDecimal gaugeMin,
    @Schema(description = "Valor máximo de la regla.", example = "5.00") BigDecimal gaugeMax,
    @Schema(description = "Precisión de lectura.", example = "0.05") BigDecimal gaugeStep,
    @Schema(description = "Capacidad en litros, si se conoce.", nullable = true, example = "20000")
        Integer capacityLiters,
    @Schema(description = "El tanque está en uso.", example = "true") boolean isActive) {}
