package co.caudal.domain.tank;

import co.caudal.domain.forecast.DailyLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A way to tell where the level of a tank is going from its recent daily levels.
 *
 * @pattern P19 Strategy
 */
public interface TrendStrategy {

  /**
   * Estimates the trend.
   *
   * @param window recent daily levels, oldest first
   * @param thresholdPerDay change per day under which the level counts as stable
   * @return the trend, or empty when the window has fewer than two days
   */
  Optional<Trend> trend(List<DailyLevel> window, BigDecimal thresholdPerDay);
}
