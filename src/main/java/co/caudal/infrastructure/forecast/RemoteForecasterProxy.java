package co.caudal.infrastructure.forecast;

import co.caudal.application.forecast.BaselineForecaster;
import co.caudal.application.forecast.BaselineForecaster.Estimate;
import co.caudal.application.forecast.ForecastCallResult;
import co.caudal.application.forecast.ForecastFailure;
import co.caudal.application.forecast.ForecastOutcome;
import co.caudal.application.forecast.ForecastRequest;
import co.caudal.application.port.out.ForecastServicePort;
import co.caudal.application.port.out.ForecasterPort;

/**
 * Stands in front of the remote forecast service (docs/Contrato-IA.md, sections 3.4 and 4): it asks
 * the {@link CircuitBreaker} before calling, tells it how the call went, and when the service
 * cannot answer it returns the simple estimate with the reason, so a forecast always comes back.
 *
 * <p>An answer about the data ({@code INSUFFICIENT_HISTORY}, a refused series) proves the service
 * is up, so it counts as a success for the breaker. When the simple estimate itself has too little
 * history, the reason is {@code INSUFFICIENT_HISTORY}.
 *
 * @pattern P11 Proxy
 */
public class RemoteForecasterProxy implements ForecasterPort {

  private final ForecastServicePort service;
  private final CircuitBreaker breaker;
  private final BaselineForecaster baseline;

  /**
   * Creates the proxy.
   *
   * @param service the remote service
   * @param breaker the circuit breaker of this instance
   * @param baseline the simple estimate
   */
  public RemoteForecasterProxy(
      ForecastServicePort service, CircuitBreaker breaker, BaselineForecaster baseline) {
    this.service = service;
    this.breaker = breaker;
    this.baseline = baseline;
  }

  @Override
  public ForecastOutcome forecast(ForecastRequest request) {
    if (!breaker.tryAcquire()) {
      return replacement(request, ForecastOutcome.CIRCUIT_OPEN);
    }
    ForecastCallResult result = service.forecast(request.series());
    return switch (result) {
      case ForecastCallResult.Success success -> {
        breaker.recordSuccess();
        yield new ForecastOutcome(success.forecast(), false, null, null);
      }
      case ForecastCallResult.Failure failure -> {
        ForecastFailure why = failure.failure();
        if (why.countsForCircuit()) {
          breaker.recordFailure();
        } else {
          breaker.recordSuccess();
        }
        yield replacement(request, why.reason());
      }
    };
  }

  private ForecastOutcome replacement(ForecastRequest request, String reason) {
    Estimate estimate = baseline.estimate(request);
    String finalReason =
        estimate.insufficientHistory() ? ForecastFailure.INSUFFICIENT_HISTORY : reason;
    return new ForecastOutcome(estimate.forecast(), true, finalReason, null);
  }
}
