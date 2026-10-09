package co.caudal.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the permission cache.
 *
 * @param cacheTtlSeconds seconds a role's permissions are kept before reading them again ({@code
 *     PERMISSION_CACHE_TTL_SECONDS}); a change of the matrix takes effect after this time
 */
@ConfigurationProperties(prefix = "caudal.security.permissions")
public record PermissionProperties(int cacheTtlSeconds) {

  /**
   * Refuses a negative lifetime; zero disables the cache.
   *
   * @throws IllegalArgumentException if the value is negative
   */
  public PermissionProperties {
    if (cacheTtlSeconds < 0) {
      throw new IllegalArgumentException("PERMISSION_CACHE_TTL_SECONDS must not be negative");
    }
  }

  /**
   * Lifetime of a cached entry.
   *
   * @return the time to live
   */
  public Duration cacheTtl() {
    return Duration.ofSeconds(cacheTtlSeconds);
  }
}
