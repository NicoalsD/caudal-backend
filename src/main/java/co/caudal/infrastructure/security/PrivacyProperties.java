package co.caudal.infrastructure.security;

import co.caudal.shared.FieldLimits;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secret that hides personal data in the database.
 *
 * @param ipHmacKey key of the HMAC applied to IP addresses and typed usernames ({@code
 *     IP_HMAC_KEY}); at least 32 bytes and no default value
 */
@ConfigurationProperties(prefix = "caudal.security.privacy")
public record PrivacyProperties(String ipHmacKey) {

  /**
   * Refuses a missing or short key. The message never includes the key.
   *
   * @throws IllegalArgumentException if the key has fewer than 32 bytes
   */
  public PrivacyProperties {
    if (ipHmacKey == null
        || ipHmacKey.getBytes(StandardCharsets.UTF_8).length < FieldLimits.HMAC_KEY_MIN_BYTES) {
      throw new IllegalArgumentException(
          "IP_HMAC_KEY must have at least " + FieldLimits.HMAC_KEY_MIN_BYTES + " bytes");
    }
  }

  @Override
  public String toString() {
    return "PrivacyProperties[REDACTED]";
  }
}
