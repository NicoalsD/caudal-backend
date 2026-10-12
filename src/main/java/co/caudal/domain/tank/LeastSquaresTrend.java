package co.caudal.domain.tank;

import co.caudal.domain.forecast.DailyLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * The slope of the least squares line through the daily levels, with the days on the x axis and
 * zero on the last day (docs/Reglas-de-negocio.md, section 10.2: 2.60, 2.35, 2.10 give -0.25 a
 * day).
 *
 * @pattern P19 Strategy
 */
public final class LeastSquaresTrend implements TrendStrategy {

  private static final int SCALE = 2;
  private static final int MIN_POINTS = 2;

  @Override
  public Optional<Trend> trend(List<DailyLevel> window, BigDecimal thresholdPerDay) {
    if (window.size() < MIN_POINTS) {
      return Optional.empty();
    }
    DailyLevel last = window.getLast();
    double[] x = new double[window.size()];
    double[] y = new double[window.size()];
    for (int i = 0; i < window.size(); i++) {
      x[i] = ChronoUnit.DAYS.between(last.day(), window.get(i).day());
      y[i] = window.get(i).value().doubleValue();
    }
    double meanX = mean(x);
    double meanY = mean(y);
    double numerator = 0;
    double denominator = 0;
    for (int i = 0; i < x.length; i++) {
      numerator += (x[i] - meanX) * (y[i] - meanY);
      denominator += (x[i] - meanX) * (x[i] - meanX);
    }
    if (denominator == 0) {
      return Optional.empty();
    }
    BigDecimal slope =
        BigDecimal.valueOf(numerator / denominator).setScale(SCALE, RoundingMode.HALF_UP);
    return Optional.of(Trend.classify(slope, thresholdPerDay));
  }

  private static double mean(double[] values) {
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }
}
