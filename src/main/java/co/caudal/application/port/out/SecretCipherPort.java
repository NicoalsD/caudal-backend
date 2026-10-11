package co.caudal.application.port.out;

import co.caudal.application.mfa.SealedSecret;

/**
 * Encrypts small secrets that the application must read back, such as the TOTP secret of a factor
 * ({@code iam.mfa_factors.secret_ciphertext}). The ciphertext is bound to a context (the owner of
 * the secret), so a ciphertext copied to another row does not decrypt.
 */
public interface SecretCipherPort {

  /**
   * Encrypts a secret with the current key.
   *
   * @param plaintext the secret
   * @param context data the ciphertext is bound to (for example the user id)
   * @return the ciphertext and the key id
   */
  SealedSecret encrypt(byte[] plaintext, String context);

  /**
   * Decrypts a secret.
   *
   * @param sealed the ciphertext and the key id
   * @param context the same context used to encrypt
   * @return the secret
   * @throws IllegalStateException if the key is unknown or the ciphertext was altered or moved
   */
  byte[] decrypt(SealedSecret sealed, String context);
}
