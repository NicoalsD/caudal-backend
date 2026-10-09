package co.caudal.infrastructure.security;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.shared.FieldLimits;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id password hasher on top of Spring Security's {@link PasswordEncoder} (BouncyCastle).
 *
 * <p>Hashes are stored in PHC format ({@code $argon2id$v=19$m=19456,t=2,p=1$salt$hash}), so every
 * hash carries its own parameters and keeps verifying after the configuration changes.
 *
 * @pattern P06 Adapter over {@link PasswordEncoder}
 */
public class Argon2PasswordHasherAdapter implements PasswordHasherPort {

  private final PasswordEncoder encoder;

  /**
   * Creates an adapter over an existing encoder (tests and the decoy hash share it).
   *
   * @param encoder the Argon2id encoder
   */
  public Argon2PasswordHasherAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  /**
   * Builds the Argon2id encoder with the configured parameters.
   *
   * @param properties memory, iterations and parallelism
   * @return the encoder
   */
  public static PasswordEncoder encoderFor(Argon2Properties properties) {
    return new Argon2PasswordEncoder(
        FieldLimits.ARGON2_SALT_BYTES,
        FieldLimits.ARGON2_HASH_BYTES,
        properties.parallelism(),
        properties.memoryKib(),
        properties.iterations());
  }

  @Override
  public String hash(NormalizedPassword password) {
    return encoder.encode(password.value());
  }

  @Override
  public boolean matches(NormalizedPassword password, String encodedHash) {
    if (encodedHash == null || password.length() > FieldLimits.PASSWORD_MAX) {
      return false;
    }
    try {
      return encoder.matches(password.value(), encodedHash);
    } catch (IllegalArgumentException malformedHash) {
      return false;
    }
  }

  @Override
  public boolean needsRehash(String encodedHash) {
    try {
      return encoder.upgradeEncoding(encodedHash);
    } catch (IllegalArgumentException malformedHash) {
      return true;
    }
  }
}
