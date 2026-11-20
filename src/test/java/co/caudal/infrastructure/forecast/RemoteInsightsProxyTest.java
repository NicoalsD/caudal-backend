package co.caudal.infrastructure.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.autopilot.InsightsAnswer;
import co.caudal.application.autopilot.InsightsCallResult;
import co.caudal.application.autopilot.InsightsRecommendations;
import co.caudal.application.autopilot.InsightsReport;
import co.caudal.application.autopilot.InsightsSnapshot;
import co.caudal.application.autopilot.ReportRequest;
import co.caudal.application.port.out.InsightsServicePort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The proxy of the assistant: the model when it answers, the rules when it cannot (P11, P18). */
class RemoteInsightsProxyTest {

  private final InsightsServicePort service = mock(InsightsServicePort.class);
  private final CircuitBreakerStateTest.ManualClock clock =
      new CircuitBreakerStateTest.ManualClock();
  private final CircuitBreaker breaker =
      new CircuitBreaker(new CircuitBreakerSettings(2, 60, 1), clock, "Insights");
  private final RemoteInsightsProxy proxy = new RemoteInsightsProxy(service, breaker, clock);

  private static final InsightsSnapshot SNAPSHOT =
      new InsightsSnapshot(
          UUID.randomUUID(),
          "Vereda",
          ZoneId.of("UTC"),
          Instant.EPOCH,
          "ASSISTED",
          new BigDecimal("25"),
          null,
          new InsightsSnapshot.Tank(
              UUID.randomUUID(),
              "T",
              new BigDecimal("3"),
              new BigDecimal("5"),
              new BigDecimal("60"),
              "LOW",
              Instant.EPOCH,
              "STABLE"),
          List.of(),
          List.of(),
          List.of(),
          List.of());

  private static final InsightsRecommendations MODEL_ANSWER =
      new InsightsRecommendations("LLM", "gemini-test", Instant.EPOCH, "ok", List.of());

  private static InsightsCallResult<InsightsRecommendations> down() {
    return new InsightsCallResult.Failure<>(true, "status 503");
  }

  @Test
  void aHealthyServiceAnswersAndTheBreakerStaysClosed() {
    when(service.recommend(SNAPSHOT)).thenReturn(new InsightsCallResult.Success<>(MODEL_ANSWER));

    assertThat(proxy.recommend(SNAPSHOT)).isSameAs(MODEL_ANSWER);
    assertThat(breaker.state().code()).isEqualTo("CLOSED");
  }

  @Test
  void aFailureIsReplacedByTheRulesAndRepeatedFailuresOpenTheCircuit() {
    when(service.recommend(SNAPSHOT)).thenReturn(down());

    assertThat(proxy.recommend(SNAPSHOT).source()).isEqualTo("RULES");
    assertThat(proxy.recommend(SNAPSHOT).model()).isEqualTo("rules-v1");
    assertThat(breaker.state().code()).isEqualTo("OPEN");
    assertThat(proxy.recommend(SNAPSHOT).source()).isEqualTo("RULES");

    verify(service, times(2)).recommend(SNAPSHOT);
  }

  @Test
  void aRefusalAboutTheRequestDoesNotCountForTheBreaker() {
    when(service.ask("¿?", SNAPSHOT))
        .thenReturn(new InsightsCallResult.Failure<>(false, "status 422"));

    for (int i = 0; i < 4; i++) {
      assertThat(proxy.ask("¿?", SNAPSHOT).source()).isEqualTo("RULES");
    }

    assertThat(breaker.state().code()).isEqualTo("CLOSED");
  }

  @Test
  void theCircuitClosesAgainAfterAHealthyCall() {
    when(service.recommend(SNAPSHOT)).thenReturn(down());
    proxy.recommend(SNAPSHOT);
    proxy.recommend(SNAPSHOT);
    clock.now = clock.now.plus(Duration.ofSeconds(61));
    when(service.recommend(SNAPSHOT)).thenReturn(new InsightsCallResult.Success<>(MODEL_ANSWER));

    assertThat(proxy.recommend(SNAPSHOT)).isSameAs(MODEL_ANSWER);
    assertThat(breaker.state().code()).isEqualTo("CLOSED");
  }

  @Test
  void anUnexpectedErrorCountsAsAFailureAndStillAnswers() {
    when(service.recommend(any())).thenThrow(new IllegalStateException("boom"));

    assertThat(proxy.recommend(SNAPSHOT).source()).isEqualTo("RULES");
    assertThat(proxy.recommend(SNAPSHOT).source()).isEqualTo("RULES");
    assertThat(breaker.state().code()).isEqualTo("OPEN");
  }

  @Test
  void theReportAndTheAnswerFallBackToTemplates() {
    ReportRequest request =
        new ReportRequest("Vereda", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Map.of());
    when(service.report(request))
        .thenReturn(new InsightsCallResult.Failure<InsightsReport>(true, "timeout"));
    when(service.ask("¿?", SNAPSHOT))
        .thenReturn(new InsightsCallResult.Failure<InsightsAnswer>(true, "timeout"));

    assertThat(proxy.report(request).source()).isEqualTo("RULES");
    assertThat(proxy.report(request).markdown()).startsWith("# Reporte");
    verify(service, never()).recommend(any());
  }
}
