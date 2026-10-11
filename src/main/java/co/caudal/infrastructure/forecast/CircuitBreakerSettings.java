package co.caudal.infrastructure.forecast;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thresholds of the circuit breaker of the forecast service (docs/Contrato-IA.md, section 4.2).
 *
 * @param failureThreshold failures in a row that open the circuit ({@code
 *     CIRCUIT_BREAKER_FAILURE_THRESHOLD})
 * @param openSeconds seconds the circuit stays open before a trial ({@code
 *     CIRCUIT_BREAKER_OPEN_SECONDS})
 * @param halfOpenCalls trial calls that must succeed to close it ({@code
 *     CIRCUIT_BREAKER_HALF_OPEN_CALLS})
 */
@ConfigurationProperties(prefix = "caudal.ia.circuit-breaker")
public record CircuitBreakerSettings(int failureThreshold, long openSeconds, int halfOpenCalls) {

  /** Refuses thresholds that are not positive. */
  public CircuitBreakerSettings {
    if (failureThreshold < 1 || openSeconds < 1 || halfOpenCalls < 1) {
      throw new IllegalArgumentException("Circuit breaker thresholds must be positive");
    }
  }

  /**
   * How long the circuit stays open.
   *
   * @return the wait
   */
  public Duration openDuration() {
    return Duration.ofSeconds(openSeconds);
  }

  /**
   * Checks that settings exist.
   *
   * @param settings the settings
   * @return the same settings
   */
  static CircuitBreakerSettings required(CircuitBreakerSettings settings) {
    return Objects.requireNonNull(settings, "settings");
  }
}
