package co.caudal.application.forecast;

/**
 * Adds the simple estimate in the shadow to every forecast the service answered (docs/Modulo-IA.md,
 * section 8): both are stored with the same input, so the evaluation compares them on the same
 * days. When the simple estimate already replaced the service, nothing is added.
 *
 * @pattern P09 Decorator
 */
public class ShadowBaselineForecaster implements Forecaster {

  private final Forecaster delegate;
  private final BaselineForecaster baseline;

  /**
   * Creates the decorator.
   *
   * @param delegate the forecaster that calls the service
   * @param baseline the simple estimate
   */
  public ShadowBaselineForecaster(Forecaster delegate, BaselineForecaster baseline) {
    this.delegate = delegate;
    this.baseline = baseline;
  }

  @Override
  public ForecastOutcome forecast(ForecastRequest request) {
    ForecastOutcome outcome = delegate.forecast(request);
    return outcome.fallback() ? outcome : outcome.withShadow(baseline.estimate(request).forecast());
  }
}
