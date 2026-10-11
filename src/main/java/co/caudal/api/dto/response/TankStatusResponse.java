package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The status of a tank.
 *
 * @param tankId the tank
 * @param level the last level, or null
 * @param trend where it goes, or null
 * @param stale whether the level is too old
 * @param band the band of the level, or null
 * @param forecast the latest forecast, or null
 * @param openAnomalies open anomalies of the aqueduct
 */
@Schema(description = "Estado del tanque: nivel, tendencia, dato viejo, banda y pronóstico.")
public record TankStatusResponse(
    @Schema(description = "Tanque.") UUID tankId,
    @Schema(description = "Último nivel efectivo; nulo sin lecturas.", nullable = true) Level level,
    @Schema(description = "Tendencia; nula con poca historia.", nullable = true) TrendView trend,
    @Schema(description = "Dato viejo.") Stale stale,
    @Schema(description = "Banda del nivel: HIGH, LOW o CRITICAL.", nullable = true) String band,
    @Schema(description = "Último pronóstico mostrado.", nullable = true)
        ForecastRunResponse forecast,
    @Schema(description = "Anomalías abiertas del acueducto.") int openAnomalies) {

  /**
   * The last level.
   *
   * @param value the level
   * @param observedAt when it was read
   * @param hoursAgo hours since then
   */
  public record Level(BigDecimal value, Instant observedAt, BigDecimal hoursAgo) {}

  /**
   * The trend.
   *
   * @param slopePerDay change per day
   * @param label DOWN, STABLE or UP
   * @param labelEs the words people read
   */
  public record TrendView(BigDecimal slopePerDay, String label, String labelEs) {}

  /**
   * Whether the level is too old.
   *
   * @param isStale true when older than the limit
   * @param staleAfterHours the limit of the rules
   */
  public record Stale(boolean isStale, int staleAfterHours) {}
}
