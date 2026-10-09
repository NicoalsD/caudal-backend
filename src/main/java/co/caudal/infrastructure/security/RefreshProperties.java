package co.caudal.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lifetime of a session in days (docs/Seguridad.md, section 2.3). The plumber ({@code OPERATOR})
 * works from a shared phone in the field, so the longer lifetime is an accepted risk.
 *
 * @param ttlDays default lifetime ({@code REFRESH_TTL_DAYS_DEFAULT})
 * @param operatorTtlDays lifetime for the {@code OPERATOR} role ({@code REFRESH_TTL_DAYS_OPERATOR})
 */
@ConfigurationProperties(prefix = "caudal.security.refresh")
public record RefreshProperties(int ttlDays, int operatorTtlDays) {

  /** Code of the role that gets the longer lifetime. */
  static final String OPERATOR_ROLE = "OPERATOR";

  /**
   * Lifetime of the session of a role.
   *
   * @param role role code in the active aqueduct
   * @return the refresh token lifetime
   */
  public Duration ttlFor(String role) {
    return Duration.ofDays(OPERATOR_ROLE.equals(role) ? operatorTtlDays : ttlDays);
  }
}
