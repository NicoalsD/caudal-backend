package co.caudal.infrastructure.security;

import co.caudal.shared.FieldLimits;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access token settings (docs/Seguridad.md, section 2.3).
 *
 * @param secret HS256 signing key ({@code JWT_SECRET}), at least 32 bytes; no default value
 * @param issuer value of the {@code iss} claim ({@code JWT_ISSUER})
 * @param audience value of the {@code aud} claim ({@code JWT_AUDIENCE})
 * @param accessTtlMinutes lifetime of an access token ({@code JWT_ACCESS_TTL_MINUTES})
 */
@ConfigurationProperties(prefix = "caudal.security.jwt")
public record JwtProperties(String secret, String issuer, String audience, int accessTtlMinutes) {

  /**
   * Refuses a configuration that would weaken the tokens. The message never includes the secret.
   *
   * @throws IllegalArgumentException if the secret is shorter than 32 bytes or issuer, audience or
   *     lifetime are missing
   */
  public JwtProperties {
    if (secret == null
        || secret.getBytes(StandardCharsets.UTF_8).length < FieldLimits.JWT_SECRET_MIN_BYTES) {
      throw new IllegalArgumentException(
          "JWT_SECRET must have at least " + FieldLimits.JWT_SECRET_MIN_BYTES + " bytes");
    }
    if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("JWT_ISSUER and JWT_AUDIENCE are required");
    }
    if (accessTtlMinutes <= 0) {
      throw new IllegalArgumentException("JWT_ACCESS_TTL_MINUTES must be positive");
    }
  }

  /**
   * Lifetime of an access token.
   *
   * @return the lifetime as a duration
   */
  public Duration accessTtl() {
    return Duration.ofMinutes(accessTtlMinutes);
  }

  /**
   * Signing key bytes.
   *
   * @return the secret as UTF-8 bytes
   */
  byte[] secretBytes() {
    return secret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + ", audience=" + audience + "]";
  }
}
