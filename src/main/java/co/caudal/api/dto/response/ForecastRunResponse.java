package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A forecast run with its points.
 *
 * @param runId the run
 * @param modelName the model
 * @param modelVersion its version
 * @param isFallback true when the simple estimate replaced the service
 * @param fallbackReason why
 * @param horizonDays days forecast
 * @param createdAt when it was made
 * @param contextFrom first moment of the history used
 * @param contextTo last moment of the history used
 * @param points one point per day
 */
@Schema(description = "Corrida de pronóstico con sus puntos p10, p50 y p90 por día.")
public record ForecastRunResponse(
    @Schema(description = "Corrida.") UUID runId,
    @Schema(description = "Modelo.", example = "autogluon/chronos-2-small") String modelName,
    @Schema(description = "Versión del modelo.") String modelVersion,
    @Schema(description = "Verdadero si la estimación simple reemplazó a la IA.")
        boolean isFallback,
    @Schema(
            description = "IA_TIMEOUT, CIRCUIT_OPEN, IA_ERROR o INSUFFICIENT_HISTORY.",
            nullable = true)
        String fallbackReason,
    @Schema(description = "Días pronosticados.") int horizonDays,
    @Schema(description = "Creación.") Instant createdAt,
    @Schema(description = "Inicio de la historia usada.") Instant contextFrom,
    @Schema(description = "Fin de la historia usada.") Instant contextTo,
    @Schema(description = "Puntos por día.") List<Point> points) {

  /**
   * One day.
   *
   * @param targetDate the day
   * @param p10 low end
   * @param p50 most likely
   * @param p90 high end
   */
  @Schema(description = "Pronóstico de un día.")
  public record Point(LocalDate targetDate, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}
}
