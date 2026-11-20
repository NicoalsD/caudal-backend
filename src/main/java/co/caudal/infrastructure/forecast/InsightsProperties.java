package co.caudal.infrastructure.forecast;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Time the backend waits for the assistant of {@code caudal-ia}. The URL and the token are the ones
 * of {@link IaProperties}; a language model answers slower than the forecast, so it has its own
 * read timeout.
 *
 * @param readTimeoutMs time to get the answer ({@code INSIGHTS_READ_TIMEOUT_MS})
 */
@ConfigurationProperties(prefix = "caudal.insights")
public record InsightsProperties(long readTimeoutMs) {

  /** Refuses a timeout that is not positive. */
  public InsightsProperties {
    if (readTimeoutMs <= 0) {
      throw new IllegalArgumentException("Insights timeout must be positive");
    }
  }

  /**
   * Time to get the answer.
   *
   * @return the timeout
   */
  public Duration readTimeout() {
    return Duration.ofMillis(readTimeoutMs);
  }
}
