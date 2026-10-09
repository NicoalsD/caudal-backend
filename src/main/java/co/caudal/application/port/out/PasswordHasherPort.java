package co.caudal.application.port.out;

import co.caudal.domain.user.NormalizedPassword;

/** Hashes and verifies passwords. The algorithm is a detail of the adapter. */
public interface PasswordHasherPort {

  /**
   * Hashes a password with a fresh random salt.
   *
   * @param password the normalized password
   * @return the encoded hash, self describing (PHC format)
   */
  String hash(NormalizedPassword password);

  /**
   * Verifies a password against a stored hash in constant time for equal sized hashes.
   *
   * @param password the normalized password
   * @param encodedHash a hash produced by {@link #hash}
   * @return true if the password matches; false for a wrong password or an unreadable hash
   */
  boolean matches(NormalizedPassword password, String encodedHash);

  /**
   * Tells whether a stored hash was made with weaker parameters than the current ones and must be
   * recalculated the next time the password is known.
   *
   * @param encodedHash a stored hash
   * @return true if the hash should be replaced
   */
  boolean needsRehash(String encodedHash);

  /**
   * Spends the time of one verification without any account behind it, so that the response time of
   * a login does not reveal whether the username exists or is usable.
   *
   * @param password the password that was typed
   */
  void verifyAgainstDecoy(NormalizedPassword password);
}
