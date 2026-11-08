package co.caudal.application.autopilot;

/**
 * How a call to the insights service went: an answer, or a failure that says whether it counts for
 * the circuit breaker. A refusal about the data (a 4xx) proves the service is up, so it does not.
 *
 * @param <T> the answer
 */
public sealed interface InsightsCallResult<T> {

  /**
   * The service answered.
   *
   * @param value the answer
   * @param <T> the answer type
   */
  record Success<T>(T value) implements InsightsCallResult<T> {}

  /**
   * The service could not answer.
   *
   * @param countsForCircuit whether the failure says the service is unhealthy
   * @param detail a short reason for the log, never data of the request
   * @param <T> the answer type
   */
  record Failure<T>(boolean countsForCircuit, String detail) implements InsightsCallResult<T> {}
}
