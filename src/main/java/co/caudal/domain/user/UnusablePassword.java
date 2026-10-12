package co.caudal.domain.user;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * A password that nobody knows: 32 random bytes. A new account is stored with its Argon2id hash, so
 * the account exists but cannot be entered until the person sets a password with the activation
 * code (docs/Seguridad.md, section 2.1). The value is created, hashed and dropped; it is never
 * shown or stored.
 */
public final class UnusablePassword {

  private static final int SIZE_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private UnusablePassword() {}

  /**
   * Generates a new unknowable password.
   *
   * @return 256 random bits, in base64url so it is a valid text for the hasher
   */
  public static NormalizedPassword generate() {
    byte[] bytes = new byte[SIZE_BYTES];
    RANDOM.nextBytes(bytes);
    return NormalizedPassword.of(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }
}
