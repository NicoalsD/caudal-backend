package co.caudal.domain.reading;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.PhysicalConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * The checks of a reading that need no data from the database (docs/Reglas-de-negocio.md, section
 * 8). The limits come from the tank (range) and from the active rule set (windows and rates); the
 * only fixed number is the clock skew of the phone, in {@link FieldLimits}. The links of {@link
 * co.caudal.domain.reading.validation.ReadingValidationHandler} use them.
 */
public final class ReadingRules {

  private static final int RATE_SCALE = 4;
  private static final long MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();

  private ReadingRules() {}

  /**
   * Tells whether a value is on the painted gauge and has at most two decimals.
   *
   * @param value the value read
   * @param gaugeMin minimum of the gauge
   * @param gaugeMax maximum of the gauge
   * @return true if valid
   */
  public static boolean isWithinGauge(BigDecimal value, BigDecimal gaugeMin, BigDecimal gaugeMax) {
    return value.stripTrailingZeros().scale() <= FieldLimits.GAUGE_DECIMALS_MAX
        && value.compareTo(gaugeMin) >= 0
        && value.compareTo(gaugeMax) <= 0;
  }

  /**
   * Tells whether an observation is in the future beyond the tolerated skew.
   *
   * @param observedAt the moment observed
   * @param now the server clock
   * @return true if later than now plus the skew
   */
  public static boolean isInFuture(Instant observedAt, Instant now) {
    return observedAt.isAfter(now.plusSeconds(FieldLimits.FUTURE_TIMESTAMP_SKEW_SECONDS));
  }

  /**
   * Tells whether an observation is older than the days the rules allow.
   *
   * @param observedAt the moment observed
   * @param now the server clock
   * @param maxBackdateDays maximum days back
   * @return true if older
   */
  public static boolean isTooOld(Instant observedAt, Instant now, int maxBackdateDays) {
    return Duration.between(observedAt, now)
            .compareTo(Duration.ofHours((long) maxBackdateDays * PhysicalConstants.HOURS_PER_DAY))
        > 0;
  }

  /**
   * Computes how fast the level moved between two readings.
   *
   * @param previousValue the earlier value
   * @param previousAt the earlier moment
   * @param value the new value
   * @param at the new moment
   * @return the absolute change per hour, or null if the hours between them are not positive
   */
  public static BigDecimal changePerHour(
      BigDecimal previousValue, Instant previousAt, BigDecimal value, Instant at) {
    long millis = Duration.between(previousAt, at).toMillis();
    if (millis <= 0) {
      return null;
    }
    return value
        .subtract(previousValue)
        .abs()
        .multiply(BigDecimal.valueOf(MILLIS_PER_HOUR))
        .divide(BigDecimal.valueOf(millis), RATE_SCALE, RoundingMode.HALF_UP);
  }
}
