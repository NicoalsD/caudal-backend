package co.caudal.domain.device;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.hash.Sha256;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * An Ed25519 public key of a device (docs/Protocolo-de-dispositivos.md, section 1.2). It is
 * accepted only if it is written as base64url without padding, decodes to exactly 32 bytes in
 * canonical form and is a point of the curve (RFC 8032, section 5.1.3). The JDK's {@code
 * KeyFactory} accepts any 32 bytes, so the point is checked here.
 */
public final class DevicePublicKey {

  /** Bit length of the field prime 2^255 - 19. */
  private static final int FIELD_BITS = 255;

  /** The field prime p = 2^255 - 19 (RFC 8032, section 5.1). */
  private static final BigInteger P =
      BigInteger.TWO.pow(FIELD_BITS).subtract(BigInteger.valueOf(19));

  /** Numerator and denominator of the curve constant d = -121665 / 121666. */
  private static final BigInteger D_NUMERATOR = BigInteger.valueOf(-121665);

  private static final BigInteger D_DENOMINATOR = BigInteger.valueOf(121666);
  private static final BigInteger D = D_NUMERATOR.multiply(D_DENOMINATOR.modInverse(P)).mod(P);

  /** Constants of the exponents of the square roots below. */
  private static final int SQRT_EXPONENT_OFFSET = 5;

  private static final int SQRT_EXPONENT_DIVISOR = 8;

  /** Exponents of the square roots: (p - 1) / 4 for sqrt(-1), (p - 5) / 8 for x. */
  private static final BigInteger SQRT_MINUS_ONE_EXPONENT =
      P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4));

  private static final BigInteger SQRT_EXPONENT =
      P.subtract(BigInteger.valueOf(SQRT_EXPONENT_OFFSET))
          .divide(BigInteger.valueOf(SQRT_EXPONENT_DIVISOR));
  private static final BigInteger SQRT_MINUS_ONE =
      BigInteger.TWO.modPow(SQRT_MINUS_ONE_EXPONENT, P);

  private static final int SIGN_BYTE = FieldLimits.ED25519_PUBLIC_KEY_BYTES - 1;
  private static final int SIGN_BIT = 0x80;

  /** JCA name of the algorithm. */
  private static final String ALGORITHM = "Ed25519";

  /** DER prefix of an Ed25519 SubjectPublicKeyInfo: SEQUENCE, OID 1.3.101.112, BIT STRING. */
  private static final String X509_PREFIX_HEX = "302a300506032b6570032100";

  private final String encoded;
  private final String fingerprint;

  private DevicePublicKey(String encoded, String fingerprint) {
    this.encoded = encoded;
    this.fingerprint = fingerprint;
  }

  /**
   * Parses the key sent by a person. Anything that is not a valid key gives empty.
   *
   * @param text the key in base64url without padding
   * @return the key, or empty if it is not a valid Ed25519 public key
   */
  public static Optional<DevicePublicKey> parse(String text) {
    if (text == null || text.length() != FieldLimits.ED25519_PUBLIC_KEY_CHARS) {
      return Optional.empty();
    }
    byte[] raw;
    try {
      raw = Base64.getUrlDecoder().decode(text);
    } catch (IllegalArgumentException invalidBase64) {
      return Optional.empty();
    }
    if (raw.length != FieldLimits.ED25519_PUBLIC_KEY_BYTES
        || !Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(text)
        || !isCurvePoint(raw)) {
      return Optional.empty();
    }
    return Optional.of(new DevicePublicKey(text, Sha256.hex(raw)));
  }

  /**
   * The key in base64url without padding, as stored.
   *
   * @return 43 characters
   */
  public String encoded() {
    return encoded;
  }

  /**
   * SHA-256 of the 32 raw bytes of the key, in lower case hexadecimal.
   *
   * @return 64 characters
   */
  public String fingerprint() {
    return fingerprint;
  }

  /**
   * Verifies an Ed25519 signature made with the private key that matches this public key (RFC
   * 8032). A malformed signature or message is simply not valid.
   *
   * @param message the bytes that were signed
   * @param signature the 64-byte signature
   * @return true only if the signature is valid for this key
   */
  public boolean verifies(byte[] message, byte[] signature) {
    try {
      PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(x509Spec());
      Signature verifier = Signature.getInstance(ALGORITHM);
      verifier.initVerify(key);
      verifier.update(message);
      return verifier.verify(signature);
    } catch (GeneralSecurityException invalid) {
      return false;
    }
  }

  /**
   * The X.509 encoding of the key: the fixed prefix of an Ed25519 SubjectPublicKeyInfo followed by
   * the 32 raw bytes, as the JDK's key factory expects.
   */
  private X509EncodedKeySpec x509Spec() {
    byte[] raw = Base64.getUrlDecoder().decode(encoded);
    byte[] prefix = HexFormat.of().parseHex(X509_PREFIX_HEX);
    byte[] der = new byte[prefix.length + raw.length];
    System.arraycopy(prefix, 0, der, 0, prefix.length);
    System.arraycopy(raw, 0, der, prefix.length, raw.length);
    return new X509EncodedKeySpec(der);
  }

  /**
   * Tells whether the 32 bytes encode a point of the curve: y below p, and x recovered from the
   * curve equation with the sign bit, where x = 0 cannot carry a set sign bit.
   *
   * @param raw the 32 bytes, little endian
   * @return true if the bytes are a point
   */
  static boolean isCurvePoint(byte[] raw) {
    boolean signBit = (raw[SIGN_BYTE] & SIGN_BIT) != 0;
    byte[] little = raw.clone();
    little[SIGN_BYTE] &= (byte) ~SIGN_BIT;
    BigInteger y = fromLittleEndian(little);
    if (y.compareTo(P) >= 0) {
      return false;
    }
    BigInteger ySquared = y.multiply(y).mod(P);
    BigInteger u = ySquared.subtract(BigInteger.ONE).mod(P);
    BigInteger v = D.multiply(ySquared).add(BigInteger.ONE).mod(P);
    BigInteger x = recoverX(u, v);
    if (x == null) {
      return false;
    }
    return x.signum() != 0 || !signBit;
  }

  /** Square root of u/v modulo p, or null if u/v is not a square (RFC 8032, 5.1.3). */
  private static BigInteger recoverX(BigInteger u, BigInteger v) {
    BigInteger v2 = v.multiply(v).mod(P);
    BigInteger v3 = v2.multiply(v).mod(P);
    BigInteger v7 = v3.multiply(v3).multiply(v).mod(P);
    BigInteger x = u.multiply(v3).multiply(u.multiply(v7).modPow(SQRT_EXPONENT, P)).mod(P);
    BigInteger check = v.multiply(x).multiply(x).mod(P);
    if (check.equals(u)) {
      return x;
    }
    if (check.equals(P.subtract(u).mod(P))) {
      return x.multiply(SQRT_MINUS_ONE).mod(P);
    }
    return null;
  }

  private static BigInteger fromLittleEndian(byte[] little) {
    byte[] big = new byte[little.length];
    for (int i = 0; i < little.length; i++) {
      big[i] = little[little.length - 1 - i];
    }
    return new BigInteger(1, big);
  }
}
