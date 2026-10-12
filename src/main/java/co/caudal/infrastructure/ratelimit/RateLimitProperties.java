package co.caudal.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limits from the environment (docs/Seguridad.md, section 2.10).
 *
 * @param loginPerMinute logins per IP per minute ({@code RATE_LIMIT_LOGIN_PER_MINUTE})
 * @param forecastPerHour forecasts per aqueduct per hour ({@code RATE_LIMIT_FORECAST_PER_HOUR})
 * @param publicReportsPerHour public damage reports per address per hour ({@code
 *     RATE_LIMIT_PUBLIC_REPORTS_PER_HOUR})
 */
@ConfigurationProperties(prefix = "caudal.security.rate-limit")
public record RateLimitProperties(
    int loginPerMinute, int forecastPerHour, int publicReportsPerHour) {

  /**
   * Refuses a limit below one.
   *
   * @throws IllegalArgumentException if a limit is not positive
   */
  public RateLimitProperties {
    if (loginPerMinute < 1 || forecastPerHour < 1 || publicReportsPerHour < 1) {
      throw new IllegalArgumentException("Rate limits must be positive");
    }
  }
}
