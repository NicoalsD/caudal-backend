package co.caudal.infrastructure.forecast;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the {@link CircuitBreakerState} of this instance of the backend (docs/Modulo-IA.md, section
 * 7.4: the state lives in memory) and moves it under a lock, so concurrent forecasts see one state.
 */
public class CircuitBreaker {

  private static final String DEFAULT_NAME = "Forecast";

  private final CircuitBreakerSettings settings;
  private final Clock clock;
  private final Logger log;
  private CircuitBreakerState state = CircuitBreakerState.initial();

  /**
   * Creates the breaker, closed.
   *
   * @param settings the thresholds
   * @param clock source of time
   */
  public CircuitBreaker(CircuitBreakerSettings settings, Clock clock) {
    this(settings, clock, DEFAULT_NAME);
  }

  /**
   * Creates a named breaker, closed. Each remote service of the backend has its own, so one that
   * fails does not stop the others.
   *
   * @param settings the thresholds
   * @param clock source of time
   * @param name the service it protects, written in the logs
   */
  public CircuitBreaker(CircuitBreakerSettings settings, Clock clock, String name) {
    this.settings = CircuitBreakerSettings.required(settings);
    this.clock = clock;
    // The name of the service goes in the name of the logger, so the messages stay constant.
    this.log = LoggerFactory.getLogger(CircuitBreaker.class.getName() + "." + name);
  }

  /**
   * Asks to let a call through.
   *
   * @return true if the call may go to the service
   */
  public synchronized boolean tryAcquire() {
    move(state.elapse(clock.instant(), settings));
    if (!state.allowsCall(settings)) {
      return false;
    }
    move(state.callStarted());
    return true;
  }

  /** Records that a call succeeded. */
  public synchronized void recordSuccess() {
    move(state.success(settings));
  }

  /** Records that a call failed in a way that counts. */
  public synchronized void recordFailure() {
    move(state.failure(clock.instant(), settings));
  }

  /**
   * The current state.
   *
   * @return the state
   */
  public synchronized CircuitBreakerState state() {
    return state;
  }

  private void move(CircuitBreakerState next) {
    if (!next.code().equals(state.code())) {
      switch (next) {
        case CircuitBreakerState.Open open -> log.warn("Circuit breaker opened");
        case CircuitBreakerState.HalfOpen half -> log.info("Circuit breaker half open");
        case CircuitBreakerState.Closed closed -> log.info("Circuit breaker closed");
      }
    }
    state = next;
  }
}
