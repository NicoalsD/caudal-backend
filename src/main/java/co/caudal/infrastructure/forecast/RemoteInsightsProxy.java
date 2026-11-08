package co.caudal.infrastructure.forecast;

import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsCallResult;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;
import co.caudal.application.autopilot.RulesInsights;
import co.caudal.application.port.out.InsightsPort;
import co.caudal.application.port.out.InsightsServicePort;
import java.time.Clock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stands in front of the remote assistant (docs/Contrato-IA.md, insights section): it asks the
 * {@link CircuitBreaker} before calling, tells it how the call went, and when the service cannot
 * answer it returns the answer of the deterministic rules ({@link RulesInsights}) with source
 * {@code RULES}, so the assistant always answers. The breaker is not the one of the forecast: an
 * unhealthy assistant must not stop the forecast, nor the other way around.
 *
 * @pattern P11 Proxy
 */
public class RemoteInsightsProxy implements InsightsPort {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteInsightsProxy.class);

  private final InsightsServicePort service;
  private final CircuitBreaker breaker;
  private final Clock clock;

  /**
   * Creates the proxy.
   *
   * @param service the remote service
   * @param breaker the circuit breaker of the assistant in this instance
   * @param clock source of time
   */
  public RemoteInsightsProxy(InsightsServicePort service, CircuitBreaker breaker, Clock clock) {
    this.service = service;
    this.breaker = breaker;
    this.clock = clock;
  }

  @Override
  public InsightsRecommendations recommend(InsightsSnapshot snapshot) {
    return through(
        () -> service.recommend(snapshot),
        () -> RulesInsights.recommend(snapshot, clock.instant()));
  }

  @Override
  public InsightsReport report(ReportRequest request) {
    return through(
        () -> service.report(request), () -> RulesInsights.report(request, clock.instant()));
  }

  @Override
  public InsightsAnswer ask(String question, InsightsSnapshot snapshot) {
    return through(
        () -> service.ask(question, snapshot), () -> RulesInsights.ask(snapshot, clock.instant()));
  }

  private <T> T through(Supplier<InsightsCallResult<T>> call, Supplier<T> rules) {
    if (!breaker.tryAcquire()) {
      return rules.get();
    }
    InsightsCallResult<T> result;
    try {
      result = call.get();
    } catch (RuntimeException unexpected) {
      breaker.recordFailure();
      LOG.warn("Insights call failed: {}", unexpected.getClass().getSimpleName());
      return rules.get();
    }
    return switch (result) {
      case InsightsCallResult.Success<T> success -> {
        breaker.recordSuccess();
        yield success.value();
      }
      case InsightsCallResult.Failure<T> failure -> {
        if (failure.countsForCircuit()) {
          breaker.recordFailure();
        } else {
          breaker.recordSuccess();
        }
        LOG.info(
            "Insights service unavailable, using rules: counts_for_circuit={}",
            failure.countsForCircuit());
        yield rules.get();
      }
    };
  }
}
