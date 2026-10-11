package co.caudal.domain.forecast;

import co.caudal.shared.FieldLimits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * "It will stay as today", with a range (docs/Contrato-IA.md, section 4.3): the level of each
 * coming day is the last level plus the median change of the history over that many days, and the
 * range comes from the 10 % and 90 % empirical quantiles of those changes. When the history has
 * fewer than {@link FieldLimits#FORECAST_BASELINE_MIN_CHANGES} changes for a horizon, the changes
 * of one day are scaled by the horizon; with fewer than that of one day, every point is the last
 * level and the estimate says the history was not enough. The values stay on the gauge.
 *
 * @pattern P19 Strategy
 */
public final class NaivePersistenceStrategy implements ForecastStrategy {

  /** Name stored in {@code forecast_runs}. */
  public static final String MODEL_NAME = "naive-persistence";

  /** Version of this algorithm. */
  public static final String MODEL_VERSION = "1";

  private static final double P10 = 0.1;
  private static final double P50 = 0.5;
  private static final double P90 = 0.9;
  private static final int SCALE = 2;

  @Override
  public String modelName() {
    return MODEL_NAME;
  }

  @Override
  public String modelVersion() {
    return MODEL_VERSION;
  }

  @Override
  public BaselineForecast forecast(
      List<DailyLevel> history, int horizonDays, BigDecimal gaugeMin, BigDecimal gaugeMax) {
    Objects.requireNonNull(history, "history");
    if (history.isEmpty() || horizonDays < 1) {
      throw new IllegalArgumentException("Need history and a positive horizon");
    }
    DailyLevel last = history.getLast();
    List<Double> daily = changes(history, 1);
    boolean insufficient = daily.size() < FieldLimits.FORECAST_BASELINE_MIN_CHANGES;
    List<BaselineForecast.Point> points = new ArrayList<>();
    for (int h = 1; h <= horizonDays; h++) {
      double low = 0;
      double mid = 0;
      double high = 0;
      if (!insufficient) {
        List<Double> span = changes(history, h);
        boolean scaled = span.size() < FieldLimits.FORECAST_BASELINE_MIN_CHANGES;
        List<Double> sample = scaled ? daily : span;
        double factor = scaled ? h : 1;
        low = quantile(sample, P10) * factor;
        mid = quantile(sample, P50) * factor;
        high = quantile(sample, P90) * factor;
      }
      points.add(
          new BaselineForecast.Point(
              last.day().plusDays(h),
              level(last.value(), low, gaugeMin, gaugeMax),
              level(last.value(), mid, gaugeMin, gaugeMax),
              level(last.value(), high, gaugeMin, gaugeMax)));
    }
    return new BaselineForecast(points, insufficient);
  }

  /** Changes between days that are exactly {@code days} apart. */
  static List<Double> changes(List<DailyLevel> history, int days) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < history.size(); i++) {
      for (int j = i + 1; j < history.size(); j++) {
        long gap = ChronoUnit.DAYS.between(history.get(i).day(), history.get(j).day());
        if (gap == days) {
          result.add(history.get(j).value().subtract(history.get(i).value()).doubleValue());
        } else if (gap > days) {
          break;
        }
      }
    }
    return result;
  }

  /** Linear interpolation between the closest ranks (the usual type 7 of statistics). */
  static double quantile(List<Double> values, double level) {
    List<Double> sorted = values.stream().sorted().toList();
    double rank = level * (sorted.size() - 1);
    int below = (int) Math.floor(rank);
    int above = (int) Math.ceil(rank);
    return sorted.get(below) + (rank - below) * (sorted.get(above) - sorted.get(below));
  }

  private static BigDecimal level(
      BigDecimal last, double change, BigDecimal gaugeMin, BigDecimal gaugeMax) {
    BigDecimal value = last.add(BigDecimal.valueOf(change)).setScale(SCALE, RoundingMode.HALF_UP);
    return value.max(gaugeMin).min(gaugeMax);
  }
}
