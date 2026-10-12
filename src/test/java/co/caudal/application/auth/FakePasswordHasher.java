package co.caudal.application.auth;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.domain.user.NormalizedPassword;

/** Hasher for use case tests: the "hash" is the password with a prefix, so it is easy to read. */
final class FakePasswordHasher implements PasswordHasherPort {

  static final String PREFIX = "hash:";

  @Override
  public String hash(NormalizedPassword password) {
    return PREFIX + password.value();
  }

  @Override
  public boolean matches(NormalizedPassword password, String encodedHash) {
    return hash(password).equals(encodedHash);
  }

  @Override
  public boolean needsRehash(String encodedHash) {
    return false;
  }

  @Override
  public void verifyAgainstDecoy(NormalizedPassword password) {
    // Nothing to spend in a test.
  }
}
