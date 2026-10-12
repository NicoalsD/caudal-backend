package co.caudal.application.forecast;

/**
 * The forecast shown to people and, when the service answered, the simple estimate kept in the
 * shadow to compare them (docs/Modulo-IA.md, section 8).
 *
 * @param primary the forecast that counts: the service's, or the simple estimate when it replaces
 *     it
 * @param fallback true when the primary forecast is the simple estimate replacing the service
 * @param fallbackReason why it was replaced ({@code IA_TIMEOUT}, {@code CIRCUIT_OPEN}, {@code
 *     IA_ERROR} or {@code INSUFFICIENT_HISTORY}), or null
 * @param shadow the simple estimate computed next to a forecast of the service, or null
 */
public record ForecastOutcome(
    ModelForecast primary, boolean fallback, String fallbackReason, ModelForecast shadow) {

  /** The circuit breaker is open and the service was not called. */
  public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";

  /** Reason stored with the simple estimate kept in the shadow. */
  public static final String SHADOW_BASELINE = "SHADOW_BASELINE";

  /**
   * The same outcome with a shadow estimate.
   *
   * @param baseline the simple estimate
   * @return the outcome
   */
  public ForecastOutcome withShadow(ModelForecast baseline) {
    return new ForecastOutcome(primary, fallback, fallbackReason, baseline);
  }
}
