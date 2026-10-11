package co.caudal.infrastructure.forecast;

import java.time.Instant;

/**
 * State of the circuit breaker in front of the forecast service (docs/Contrato-IA.md, section 4.2;
 * docs/Modulo-IA.md, section 7). Each state decides whether a call may go to the service and which
 * state follows a success, a failure or the passing of time; the states are immutable.
 *
 * <ul>
 *   <li>{@link Closed}: calls go through; {@code failure_threshold} failures in a row open it.
 *   <li>{@link Open}: no call goes through; after {@code open_seconds} it lets a trial through.
 *   <li>{@link HalfOpen}: {@code half_open_calls} trial calls; all must succeed to close it, and
 *       any failure opens it again.
 * </ul>
 *
 * @pattern P18 State
 */
public sealed interface CircuitBreakerState
    permits CircuitBreakerState.Closed, CircuitBreakerState.Open, CircuitBreakerState.HalfOpen {

  /**
   * The name shown in logs and health: {@code CLOSED}, {@code OPEN} or {@code HALF_OPEN}.
   *
   * @return the name
   */
  String code();

  /**
   * The state after time passed, before deciding about a call.
   *
   * @param now the current moment
   * @param settings the thresholds
   * @return the same state, or {@link HalfOpen} when an open circuit waited enough
   */
  default CircuitBreakerState elapse(Instant now, CircuitBreakerSettings settings) {
    return this;
  }

  /**
   * Tells whether a call may go to the service now.
   *
   * @param settings the thresholds
   * @return true if the call is allowed
   */
  boolean allowsCall(CircuitBreakerSettings settings);

  /**
   * The state after a call was let through (a half open circuit counts its trials).
   *
   * @return the state
   */
  default CircuitBreakerState callStarted() {
    return this;
  }

  /**
   * The state after a call succeeded.
   *
   * @param settings the thresholds
   * @return the state
   */
  CircuitBreakerState success(CircuitBreakerSettings settings);

  /**
   * The state after a call failed in a way that counts.
   *
   * @param now the current moment
   * @param settings the thresholds
   * @return the state
   */
  CircuitBreakerState failure(Instant now, CircuitBreakerSettings settings);

  /**
   * The starting state.
   *
   * @return a closed circuit without failures
   */
  static CircuitBreakerState initial() {
    return new Closed(0);
  }

  /**
   * Calls go through.
   *
   * @param failures failures in a row so far
   * @pattern P18 State
   */
  record Closed(int failures) implements CircuitBreakerState {

    @Override
    public String code() {
      return "CLOSED";
    }

    @Override
    public boolean allowsCall(CircuitBreakerSettings settings) {
      return true;
    }

    @Override
    public CircuitBreakerState success(CircuitBreakerSettings settings) {
      return failures == 0 ? this : new Closed(0);
    }

    @Override
    public CircuitBreakerState failure(Instant now, CircuitBreakerSettings settings) {
      int count = failures + 1;
      return count >= settings.failureThreshold() ? new Open(now) : new Closed(count);
    }
  }

  /**
   * No call goes through until the wait ends.
   *
   * @param openedAt when it opened
   * @pattern P18 State
   */
  record Open(Instant openedAt) implements CircuitBreakerState {

    @Override
    public String code() {
      return "OPEN";
    }

    @Override
    public CircuitBreakerState elapse(Instant now, CircuitBreakerSettings settings) {
      return now.isBefore(openedAt.plus(settings.openDuration())) ? this : new HalfOpen(0, 0);
    }

    @Override
    public boolean allowsCall(CircuitBreakerSettings settings) {
      return false;
    }

    @Override
    public CircuitBreakerState success(CircuitBreakerSettings settings) {
      return this;
    }

    @Override
    public CircuitBreakerState failure(Instant now, CircuitBreakerSettings settings) {
      return new Open(now);
    }
  }

  /**
   * A few trial calls decide whether the service is back.
   *
   * @param started trial calls let through
   * @param succeeded trial calls that succeeded
   * @pattern P18 State
   */
  record HalfOpen(int started, int succeeded) implements CircuitBreakerState {

    @Override
    public String code() {
      return "HALF_OPEN";
    }

    @Override
    public boolean allowsCall(CircuitBreakerSettings settings) {
      return started < settings.halfOpenCalls();
    }

    @Override
    public CircuitBreakerState callStarted() {
      return new HalfOpen(started + 1, succeeded);
    }

    @Override
    public CircuitBreakerState success(CircuitBreakerSettings settings) {
      int count = succeeded + 1;
      return count >= settings.halfOpenCalls() ? new Closed(0) : new HalfOpen(started, count);
    }

    @Override
    public CircuitBreakerState failure(Instant now, CircuitBreakerSettings settings) {
      return new Open(now);
    }
  }
}
