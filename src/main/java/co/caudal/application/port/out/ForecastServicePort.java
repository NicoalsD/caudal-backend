package co.caudal.application.port.out;

import co.caudal.application.forecast.ForecastCallResult;
import co.caudal.application.forecast.ForecastSeries;

/** Asks the forecast service ({@code caudal-ia}) for a forecast of a tank. */
public interface ForecastServicePort {

  /**
   * Forecasts a series. It never throws for an answer of the service: every problem comes back as a
   * {@link ForecastCallResult.Failure}.
   *
   * @param series the history and the horizon
   * @return the forecast or why there is none
   */
  ForecastCallResult forecast(ForecastSeries series);
}
