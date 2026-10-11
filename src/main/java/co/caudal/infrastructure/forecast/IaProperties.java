package co.caudal.infrastructure.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the forecast service is and how the backend proves who it is (docs/Contrato-IA.md, section
 * 6.1).
 *
 * @param serviceUrl base URL of {@code caudal-ia} without the final slash ({@code IA_SERVICE_URL});
 *     empty when there is no service, and then every forecast is the simple estimate
 * @param serviceToken service token ({@code IA_SERVICE_TOKEN}); never logged
 */
@ConfigurationProperties(prefix = "caudal.ia")
public record IaProperties(String serviceUrl, String serviceToken) {

  /** Normalizes missing values to empty texts. */
  public IaProperties {
    serviceUrl = serviceUrl == null ? "" : serviceUrl.strip();
    serviceToken = serviceToken == null ? "" : serviceToken;
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
