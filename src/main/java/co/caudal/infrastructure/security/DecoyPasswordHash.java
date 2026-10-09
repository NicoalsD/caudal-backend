package co.caudal.infrastructure.security;

import co.caudal.domain.user.NormalizedPassword;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Decoy hash that equalizes the login time when the account does not exist, is locked or is
 * disabled (docs/Seguridad.md, section 2.2).
 *
 * <p>It is built at startup with the same encoder, and therefore the same Argon2id parameters, as
 * the real hashes, from a random password that nobody knows and that is discarded. Verifying
 * against it costs the same as verifying a real hash, so the response time does not tell whether
 * the username exists.
 */
final class DecoyPasswordHash {

  private static final int DECOY_PASSWORD_BYTES = 32;

  private final PasswordEncoder encoder;
  private final String decoyHash;

  /**
   * Creates the decoy with a fresh random secret.
   *
   * @param encoder the encoder used for the real hashes
   */
  DecoyPasswordHash(PasswordEncoder encoder) {
    this.encoder = encoder;
    byte[] secret = new byte[DECOY_PASSWORD_BYTES];
    new SecureRandom().nextBytes(secret);
    this.decoyHash = encoder.encode(Base64.getEncoder().encodeToString(secret));
  }

  /**
   * Spends the time of one verification.
   *
   * @param password the password that was typed
   * @return the verification result, which is false because the decoy secret is unknown
   */
  boolean verify(NormalizedPassword password) {
    return encoder.matches(password.value(), decoyHash);
  }

  /**
   * Returns the decoy hash, only to let tests check that its parameters follow the encoder.
   *
   * @return the encoded decoy hash
   */
  String encodedHash() {
    return decoyHash;
  }
}
