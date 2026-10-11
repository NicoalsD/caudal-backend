package co.caudal.application.forecast;

import co.caudal.domain.forecast.DailyLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Everything needed to forecast a tank, by the service or by the simple estimate.
 *
 * @param series the effective levels sent to the service
 * @param daily the same history as one level per day, for the simple estimate
 * @param gaugeMin lowest value of the gauge
 * @param gaugeMax highest value of the gauge
 */
public record ForecastRequest(
    ForecastSeries series, List<DailyLevel> daily, BigDecimal gaugeMin, BigDecimal gaugeMax) {

  /** Copies the history. */
  public ForecastRequest {
    Objects.requireNonNull(series, "series");
    daily = List.copyOf(daily);
  }
}
