package co.caudal.infrastructure.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerStateTest {

  private static final CircuitBreakerSettings SETTINGS = new CircuitBreakerSettings(5, 60, 2);

  /** A clock that the test moves by hand. */
  static final class ManualClock extends Clock {
    Instant now = Instant.parse("2026-10-11T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void fiveFailuresInARowOpenTheCircuitAndASuccessResetsTheCount() {
    ManualClock clock = new ManualClock();
    CircuitBreaker breaker = new CircuitBreaker(SETTINGS, clock);

    for (int i = 0; i < 4; i++) {
      assertThat(breaker.tryAcquire()).isTrue();
      breaker.recordFailure();
    }
    breaker.recordSuccess();
    assertThat(breaker.state()).isEqualTo(new CircuitBreakerState.Closed(0));
    for (int i = 0; i < 5; i++) {
      breaker.recordFailure();
    }

    assertThat(breaker.state().code()).isEqualTo("OPEN");
    assertThat(breaker.tryAcquire()).isFalse();
  }

  @Test
  void afterTheWaitTwoGoodTrialsCloseIt() {
    ManualClock clock = new ManualClock();
    CircuitBreaker breaker = open(clock);

    clock.now = clock.now.plus(Duration.ofSeconds(59));
    assertThat(breaker.tryAcquire()).isFalse();
    clock.now = clock.now.plus(Duration.ofSeconds(1));
    assertThat(breaker.tryAcquire()).isTrue();
    assertThat(breaker.state().code()).isEqualTo("HALF_OPEN");
    assertThat(breaker.tryAcquire()).isTrue();
    assertThat(breaker.tryAcquire()).as("only two trials at a time").isFalse();
    breaker.recordSuccess();
    assertThat(breaker.state().code()).isEqualTo("HALF_OPEN");
    breaker.recordSuccess();

    assertThat(breaker.state()).isEqualTo(new CircuitBreakerState.Closed(0));
  }

  @Test
  void aFailedTrialOpensItAgainAndRestartsTheWait() {
    ManualClock clock = new ManualClock();
    CircuitBreaker breaker = open(clock);
    clock.now = clock.now.plus(Duration.ofSeconds(60));
    assertThat(breaker.tryAcquire()).isTrue();

    breaker.recordFailure();

    assertThat(breaker.state()).isEqualTo(new CircuitBreakerState.Open(clock.now));
    clock.now = clock.now.plus(Duration.ofSeconds(30));
    assertThat(breaker.tryAcquire()).isFalse();
  }

  @Test
  void anOpenCircuitIgnoresSuccessesAndRenewsOnFailures() {
    Instant at = Instant.EPOCH;
    CircuitBreakerState open = new CircuitBreakerState.Open(at);

    assertThat(open.success(SETTINGS)).isSameAs(open);
    assertThat(open.failure(at.plusSeconds(5), SETTINGS))
        .isEqualTo(new CircuitBreakerState.Open(at.plusSeconds(5)));
    assertThat(open.callStarted()).isSameAs(open);
    assertThat(CircuitBreakerState.initial().elapse(at, SETTINGS))
        .isEqualTo(CircuitBreakerState.initial());
    assertThatThrownBy(() -> new CircuitBreakerSettings(0, 60, 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static CircuitBreaker open(ManualClock clock) {
    CircuitBreaker breaker = new CircuitBreaker(SETTINGS, clock);
    for (int i = 0; i < 5; i++) {
      breaker.recordFailure();
    }
    return breaker;
  }
}
