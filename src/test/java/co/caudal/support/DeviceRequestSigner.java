package co.caudal.support;

import co.caudal.application.device.auth.SignedHeaders;
import co.caudal.domain.device.CanonicalRequest;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Signs requests the way a device does (docs/Protocolo-de-dispositivos.md, section 2): a fresh
 * Ed25519 pair, the public key as base64url and the signature over the canonical string.
 */
public final class DeviceRequestSigner {

  /** Bytes of the raw public key at the end of the JDK's X.509 encoding. */
  private static final int RAW_KEY_BYTES = 32;

  private DeviceRequestSigner() {}

  /**
   * A new Ed25519 key pair, as a device generates it.
   *
   * @return the pair
   * @throws GeneralSecurityException if the algorithm is missing
   */
  public static KeyPair newKeyPair() throws GeneralSecurityException {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  /**
   * The public key as the API expects it: the 32 raw bytes in base64url without padding.
   *
   * @param pair the key pair
   * @return 43 characters
   */
  public static String publicKeyText(KeyPair pair) {
    byte[] encoded = pair.getPublic().getEncoded();
    byte[] raw = Arrays.copyOfRange(encoded, encoded.length - RAW_KEY_BYTES, encoded.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  /**
   * Signs a request.
   *
   * @param pair the key pair of the device
   * @param method the method
   * @param path the path with the prefix
   * @param timestamp the timestamp header
   * @param nonce the nonce header
   * @param body the raw body
   * @return the signature in base64url without padding
   * @throws GeneralSecurityException if the algorithm is missing
   */
  public static String sign(
      KeyPair pair, String method, String path, String timestamp, String nonce, byte[] body)
      throws GeneralSecurityException {
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(pair.getPrivate());
    signer.update(CanonicalRequest.of(method, path, timestamp, nonce, body));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
  }

  /**
   * The four signature headers, each sent once.
   *
   * @param deviceId the device
   * @param timestamp the timestamp
   * @param nonce the nonce
   * @param signature the signature
   * @return the headers
   */
  public static SignedHeaders headers(
      String deviceId, String timestamp, String nonce, String signature) {
    return new SignedHeaders(
        List.of(deviceId), List.of(timestamp), List.of(nonce), List.of(signature));
  }
}
