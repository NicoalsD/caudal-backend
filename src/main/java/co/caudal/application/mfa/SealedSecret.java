package co.caudal.application.mfa;

/**
 * An encrypted secret and the key that encrypted it.
 *
 * @param ciphertext nonce, ciphertext and authentication tag
 * @param keyId id of the key, kept to allow rotation
 */
public record SealedSecret(byte[] ciphertext, String keyId) {

  /** Copies the bytes so the record cannot be changed from outside. */
  public SealedSecret {
    ciphertext = ciphertext.clone();
  }

  @Override
  public byte[] ciphertext() {
    return ciphertext.clone();
  }
}
