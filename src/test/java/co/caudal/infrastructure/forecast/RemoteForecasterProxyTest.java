package co.caudal.infrastructure.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.forecast.BaselineForecaster;
import co.caudal.application.forecast.ForecastCallResult;
import co.caudal.application.forecast.ForecastFailure;
import co.caudal.application.forecast.ForecastOutcome;
import co.caudal.application.forecast.ForecastPoint;
import co.caudal.application.forecast.ForecastRequest;
import co.caudal.application.forecast.ModelForecast;
import co.caudal.application.port.out.ForecastServicePort;
import co.caudal.domain.forecast.DailyLevel;
import co.caudal.domain.forecast.NaivePersistenceStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteForecasterProxyTest {

  private static final ModelForecast CHRONOS =
      new ModelForecast(
          "autogluon/chronos-2-small",
          "2.0.0",
          List.of(
              new ForecastPoint(
                  LocalDate.of(2026, 10, 9),
                  new BigDecimal("1.80"),
                  new BigDecimal("2.10"),
                  new BigDecimal("2.40"))),
          120);

  private final ForecastServicePort service = mock(ForecastServicePort.class);
  private final CircuitBreakerStateTest.ManualClock clock =
      new CircuitBreakerStateTest.ManualClock();
  private final CircuitBreaker breaker =
      new CircuitBreaker(new CircuitBreakerSettings(5, 60, 2), clock);
  private final RemoteForecasterProxy proxy =
      new RemoteForecasterProxy(
          service, breaker, new BaselineForecaster(new NaivePersistenceStrategy()));

  static ForecastRequest request(int days) {
    List<DailyLevel> daily = new ArrayList<>();
    for (int i = 0; i < days; i++) {
      daily.add(
          new DailyLevel(
              LocalDate.of(2026, 10, 9 - days + i),
              new BigDecimal("3.00")
                  .subtract(new BigDecimal("0.10").multiply(BigDecimal.valueOf(i)))));
    }
    return new ForecastRequest(
        IaForecastClientAdapterTest.series(1), daily, BigDecimal.ZERO, new BigDecimal("5.00"));
  }

  private static ForecastCallResult failure(String reason, boolean counts) {
    return new ForecastCallResult.Failure(new ForecastFailure(reason, counts, "test"));
  }

  @Test
  void aGoodAnswerOfTheServiceIsThePrimaryForecast() {
    when(service.forecast(any())).thenReturn(new ForecastCallResult.Success(CHRONOS));

    ForecastOutcome outcome = proxy.forecast(request(6));

    assertThat(outcome.primary()).isEqualTo(CHRONOS);
    assertThat(outcome.fallback()).isFalse();
    assertThat(outcome.fallbackReason()).isNull();
  }

  @Test
  void aTimeoutIsReplacedByTheSimpleEstimate() {
    when(service.forecast(any())).thenReturn(failure("IA_TIMEOUT", true));

    ForecastOutcome outcome = proxy.forecast(request(6));

    assertThat(outcome.fallback()).isTrue();
    assertThat(outcome.fallbackReason()).isEqualTo("IA_TIMEOUT");
    assertThat(outcome.primary().modelName()).isEqualTo("naive-persistence");
    assertThat(outcome.primary().points().getFirst().p50()).isEqualByComparingTo("2.40");
  }

  @Test
  void fiveCountingFailuresOpenTheCircuitAndTheServiceIsNoLongerCalled() {
    when(service.forecast(any())).thenReturn(failure("IA_ERROR", true));
    for (int i = 0; i < 5; i++) {
      proxy.forecast(request(6));
    }
    org.mockito.Mockito.clearInvocations(service);

    ForecastOutcome outcome = proxy.forecast(request(6));

    assertThat(outcome.fallbackReason()).isEqualTo("CIRCUIT_OPEN");
    verify(service, never()).forecast(any());
  }

  @Test
  void anAnswerAboutTheDataDoesNotOpenTheCircuit() {
    when(service.forecast(any())).thenReturn(failure("IA_ERROR", false));
    for (int i = 0; i < 10; i++) {
      proxy.forecast(request(6));
    }

    assertThat(breaker.state().code()).isEqualTo("CLOSED");
  }

  @Test
  void withTooLittleHistoryTheReasonIsInsufficientHistory() {
    when(service.forecast(any())).thenReturn(failure("IA_TIMEOUT", true));

    ForecastOutcome outcome = proxy.forecast(request(2));

    assertThat(outcome.fallbackReason()).isEqualTo("INSUFFICIENT_HISTORY");
  }
}
