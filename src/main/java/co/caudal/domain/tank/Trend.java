package co.caudal.domain.tank;

import java.math.BigDecimal;

/**
 * Where the level is going.
 *
 * @param slopePerDay change per day, negative when it goes down
 * @param label {@code DOWN}, {@code STABLE} or {@code UP}
 * @param labelEs the words people read: "viene bajando", "estable" or "sube"
 */
public record Trend(BigDecimal slopePerDay, String label, String labelEs) {

  /**
   * Classifies a slope with the threshold of the rules (docs/Reglas-de-negocio.md, section 10.2).
   *
   * @param slope change per day
   * @param threshold {@code trend_threshold_per_day}
   * @return the trend
   */
  public static Trend classify(BigDecimal slope, BigDecimal threshold) {
    if (slope.compareTo(threshold.negate()) < 0) {
      return new Trend(slope, "DOWN", "viene bajando");
    }
    if (slope.compareTo(threshold) > 0) {
      return new Trend(slope, "UP", "sube");
    }
    return new Trend(slope, "STABLE", "estable");
  }
}
