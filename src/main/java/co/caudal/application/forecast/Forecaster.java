package co.caudal.application.forecast;

/**
 * Something that forecasts a tank and always answers. The remote proxy, its shadow decorator and
 * the use cases share this interface.
 */
public interface Forecaster {

  /**
   * Forecasts a tank.
   *
   * @param request the history, the horizon and the gauge
   * @return the forecast
   */
  ForecastOutcome forecast(ForecastRequest request);
}
