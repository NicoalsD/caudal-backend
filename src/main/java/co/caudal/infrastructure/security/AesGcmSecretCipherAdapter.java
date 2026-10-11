package co.caudal.infrastructure.security;

import co.caudal.application.mfa.SealedSecret;
import co.caudal.application.port.out.SecretCipherPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts TOTP secrets with AES-256-GCM (docs/Seguridad.md, section 2.4). Each ciphertext is a
 * random 96-bit nonce followed by the ciphertext and its 128-bit tag; the key id and the context
 * (the owner of the secret) go in the associated data, so a ciphertext moved to another account or
 * read with another key fails to decrypt.
 *
 * @pattern P06 Adapter
 */
public class AesGcmSecretCipherAdapter implements SecretCipherPort {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom random = new SecureRandom();
  private final String currentKeyId;
  private final Map<String, SecretKey> keys;

  /**
   * Creates the adapter.
   *
   * @param properties the current key and its id
   */
  public AesGcmSecretCipherAdapter(MfaProperties properties) {
    this.currentKeyId = properties.keyId();
    this.keys = Map.of(currentKeyId, new SecretKeySpec(properties.keyBytes(), KEY_ALGORITHM));
  }

  @Override
  public SealedSecret encrypt(byte[] plaintext, String context) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(associatedData(currentKeyId, context));
      byte[] body = cipher.doFinal(plaintext);
      return new SealedSecret(
          ByteBuffer.allocate(NONCE_BYTES + body.length).put(nonce).put(body).array(),
          currentKeyId);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Could not encrypt the secret", failure);
    }
  }

  @Override
  public byte[] decrypt(SealedSecret sealed, String context) {
    SecretKey key = keys.get(sealed.keyId());
    if (key == null || sealed.ciphertext().length <= NONCE_BYTES) {
      throw new IllegalStateException("Unknown key or malformed ciphertext");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(TAG_BITS, sealed.ciphertext(), 0, NONCE_BYTES));
      cipher.updateAAD(associatedData(sealed.keyId(), context));
      return cipher.doFinal(
          sealed.ciphertext(), NONCE_BYTES, sealed.ciphertext().length - NONCE_BYTES);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Could not decrypt the secret", failure);
    }
  }

  private static byte[] associatedData(String keyId, String context) {
    return ("caudal-mfa|" + keyId + "|" + context).getBytes(StandardCharsets.UTF_8);
  }
}
