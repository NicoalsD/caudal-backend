package co.caudal.infrastructure.security;

import co.caudal.application.port.out.PrivacyHasherPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 with {@code IP_HMAC_KEY}. The database keeps this value instead of the IP address, so
 * attempts from the same address can be counted without being able to recover the address.
 */
public class HmacSha256HasherAdapter implements PrivacyHasherPort {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  /**
   * Creates the hasher.
   *
   * @param properties validated key settings
   */
  public HmacSha256HasherAdapter(PrivacyProperties properties) {
    this.key =
        new SecretKeySpec(properties.ipHmacKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
  }

  @Override
  public String hmac(String value) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ALGORITHM + " is required by the Java platform", ex);
    }
  }
}
