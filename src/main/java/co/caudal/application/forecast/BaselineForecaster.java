package co.caudal.application.forecast;

import co.caudal.domain.forecast.BaselineForecast;
import co.caudal.domain.forecast.ForecastStrategy;
import java.util.List;

/**
 * Runs the simple estimate of a {@link ForecastStrategy} and gives it the shape of a model forecast
 * (docs/Contrato-IA.md, section 4.3).
 */
public class BaselineForecaster {

  private final ForecastStrategy strategy;

  /**
   * Creates the forecaster.
   *
   * @param strategy the estimate to run
   */
  public BaselineForecaster(ForecastStrategy strategy) {
    this.strategy = strategy;
  }

  /**
   * The result of the simple estimate.
   *
   * @param forecast the estimate
   * @param insufficientHistory true if there was too little history for a range
   */
  public record Estimate(ModelForecast forecast, boolean insufficientHistory) {}

  /**
   * Estimates the horizon of a request.
   *
   * @param request the history and the gauge
   * @return the estimate
   */
  public Estimate estimate(ForecastRequest request) {
    long started = System.nanoTime();
    BaselineForecast baseline =
        strategy.forecast(
            request.daily(),
            request.series().horizonDays(),
            request.gaugeMin(),
            request.gaugeMax());
    List<ForecastPoint> points =
        baseline.points().stream()
            .map(p -> new ForecastPoint(p.day(), p.p10(), p.p50(), p.p90()))
            .toList();
    long latencyMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
    return new Estimate(
        new ModelForecast(strategy.modelName(), strategy.modelVersion(), points, latencyMs),
        baseline.insufficientHistory());
  }
}
