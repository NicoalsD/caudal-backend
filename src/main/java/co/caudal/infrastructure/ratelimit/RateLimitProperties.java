package co.caudal.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limits from the environment (docs/Seguridad.md, section 2.10).
 *
 * @param loginPerMinute logins per IP per minute ({@code RATE_LIMIT_LOGIN_PER_MINUTE})
 */
@ConfigurationProperties(prefix = "caudal.security.rate-limit")
public record RateLimitProperties(int loginPerMinute) {

  /**
   * Refuses a limit below one.
   *
   * @throws IllegalArgumentException if a limit is not positive
   */
  public RateLimitProperties {
    if (loginPerMinute < 1) {
      throw new IllegalArgumentException("RATE_LIMIT_LOGIN_PER_MINUTE must be positive");
    }
  }
}
