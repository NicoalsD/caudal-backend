package co.caudal.domain.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The simple estimate of the coming days.
 *
 * @param points one point per day of the horizon
 * @param insufficientHistory true when there was too little history for a range and every point is
 *     the last level
 */
public record BaselineForecast(List<Point> points, boolean insufficientHistory) {

  /** Copies the points. */
  public BaselineForecast {
    points = List.copyOf(points);
  }

  /**
   * One day of the estimate.
   *
   * @param day the day
   * @param p10 low end of the range
   * @param p50 most likely level
   * @param p90 high end of the range
   */
  public record Point(LocalDate day, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}
}
