package co.caudal.application.forecast;

/** The outcome of a call to the forecast service: a forecast or a failure. */
public sealed interface ForecastCallResult
    permits ForecastCallResult.Success, ForecastCallResult.Failure {

  /**
   * The service answered a valid forecast.
   *
   * @param forecast the forecast
   */
  record Success(ModelForecast forecast) implements ForecastCallResult {}

  /**
   * The service did not give a usable forecast.
   *
   * @param failure why
   */
  record Failure(ForecastFailure failure) implements ForecastCallResult {}
}
