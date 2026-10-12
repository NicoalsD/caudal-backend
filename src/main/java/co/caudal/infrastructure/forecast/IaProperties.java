package co.caudal.infrastructure.forecast;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the forecast service is and how the backend proves who it is (docs/Contrato-IA.md, section
 * 6.1).
 *
 * @param serviceUrl base URL of {@code caudal-ia} without the final slash ({@code IA_SERVICE_URL});
 *     empty when there is no service, and then every forecast is the simple estimate
 * @param serviceToken service token ({@code IA_SERVICE_TOKEN}); never logged
 * @param connectTimeoutMs time to open the connection ({@code IA_CONNECT_TIMEOUT_MS})
 * @param readTimeoutMs time to get the answer ({@code IA_READ_TIMEOUT_MS})
 */
@ConfigurationProperties(prefix = "caudal.ia")
public record IaProperties(
    String serviceUrl, String serviceToken, long connectTimeoutMs, long readTimeoutMs) {

  /** Normalizes missing values and refuses timeouts that are not positive. */
  public IaProperties {
    serviceUrl = serviceUrl == null ? "" : serviceUrl.strip();
    serviceToken = serviceToken == null ? "" : serviceToken;
    if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
      throw new IllegalArgumentException("IA timeouts must be positive");
    }
  }

  /**
   * Time to open the connection.
   *
   * @return the timeout
   */
  public Duration connectTimeout() {
    return Duration.ofMillis(connectTimeoutMs);
  }

  /**
   * Time to get the answer.
   *
   * @return the timeout
   */
  public Duration readTimeout() {
    return Duration.ofMillis(readTimeoutMs);
  }

  /**
   * Tells whether a service is configured.
   *
   * @return true when both the URL and the token are set
   */
  public boolean configured() {
    return !serviceUrl.isEmpty() && !serviceToken.isEmpty();
  }

  @Override
  public String toString() {
    return "IaProperties[serviceUrl=" + serviceUrl + ", serviceToken=REDACTED]";
  }
}
