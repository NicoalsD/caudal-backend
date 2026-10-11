package co.caudal.infrastructure.security;

import co.caudal.shared.FieldLimits;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key that encrypts TOTP secrets (docs/Seguridad.md, section 2.4).
 *
 * @param encryptionKey AES-256 key in Base64 ({@code MFA_ENCRYPTION_KEY}); exactly 32 bytes and no
 *     default value
 * @param keyId id stored next to each ciphertext ({@code MFA_KEY_ID}), to allow rotation
 * @param issuer name shown by the authenticator app
 */
@ConfigurationProperties(prefix = "caudal.security.mfa")
public record MfaProperties(String encryptionKey, String keyId, String issuer) {

  /**
   * Refuses a missing or badly sized key. The message never includes the key.
   *
   * @throws IllegalArgumentException if the key is not 32 bytes in Base64 or the id is missing
   */
  public MfaProperties {
    if (encryptionKey == null || keyId == null || keyId.isBlank() || issuer == null) {
      throw new IllegalArgumentException("MFA_ENCRYPTION_KEY and MFA_KEY_ID are required");
    }
    byte[] key;
    try {
      key = Base64.getDecoder().decode(encryptionKey);
    } catch (IllegalArgumentException notBase64) {
      throw new IllegalArgumentException("MFA_ENCRYPTION_KEY must be Base64", notBase64);
    }
    if (key.length != FieldLimits.MFA_ENCRYPTION_KEY_BYTES) {
      throw new IllegalArgumentException(
          "MFA_ENCRYPTION_KEY must have " + FieldLimits.MFA_ENCRYPTION_KEY_BYTES + " bytes");
    }
  }

  /**
   * The decoded key.
   *
   * @return the 32 bytes of the key
   */
  public byte[] keyBytes() {
    return Base64.getDecoder().decode(encryptionKey);
  }

  @Override
  public String toString() {
    return "MfaProperties[keyId=" + keyId + ", key=REDACTED]";
  }
}
