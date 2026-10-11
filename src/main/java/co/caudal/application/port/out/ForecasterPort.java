package co.caudal.application.port.out;

import co.caudal.application.forecast.ForecastOutcome;
import co.caudal.application.forecast.ForecastRequest;

/** Forecasts a tank and always answers: with the service, or with the simple estimate. */
public interface ForecasterPort {

  /**
   * Forecasts a tank.
   *
   * @param request the history, the horizon and the gauge
   * @return the forecast
   */
  ForecastOutcome forecast(ForecastRequest request);
}
