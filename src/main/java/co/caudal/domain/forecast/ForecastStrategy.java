package co.caudal.domain.forecast;

import java.math.BigDecimal;
import java.util.List;

/**
 * A way to estimate the coming levels of a tank from its own history, without the forecast service
 * (docs/Contrato-IA.md, section 4.3).
 *
 * @pattern P19 Strategy
 */
public interface ForecastStrategy {

  /**
   * Name stored in {@code forecast_runs.model_name}.
   *
   * @return the name
   */
  String modelName();

  /**
   * Version stored in {@code forecast_runs.model_version}.
   *
   * @return the version
   */
  String modelVersion();

  /**
   * Estimates the days after the last one of the history.
   *
   * @param history daily levels, oldest first, at least one
   * @param horizonDays days to estimate
   * @param gaugeMin lowest value of the gauge
   * @param gaugeMax highest value of the gauge
   * @return the estimate
   */
  BaselineForecast forecast(
      List<DailyLevel> history, int horizonDays, BigDecimal gaugeMin, BigDecimal gaugeMax);
}
