package co.caudal.domain.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevicePublicKeyTest {

  /** Public key of the first test vector of RFC 8032, section 7.1. */
  private static final String RFC_KEY_HEX =
      "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a";

  private static final BigInteger P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
  private static final BigInteger D =
      BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

  private static String encode(byte[] raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private static byte[] hex(String text) {
    return HexFormat.of().parseHex(text);
  }

  @Test
  void acceptsTheRfcVectorAndComputesItsFingerprint() throws Exception {
    byte[] raw = hex(RFC_KEY_HEX);
    DevicePublicKey key = DevicePublicKey.parse(encode(raw)).orElseThrow();

    assertThat(key.encoded()).isEqualTo(encode(raw)).hasSize(43);
    assertThat(key.fingerprint())
        .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)))
        .hasSize(64)
        .matches("[0-9a-f]{64}");
  }

  @Test
  void acceptsKeysGeneratedByTheJdk() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
    for (int i = 0; i < 50; i++) {
      KeyPair pair = generator.generateKeyPair();
      byte[] encoded = pair.getPublic().getEncoded();
      String text =
          encode(java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
      assertThat(DevicePublicKey.parse(text)).isPresent();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "abc",
        "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHUR",
        "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo="
      })
  void rejectsTextThatIsNot43Base64UrlCharactersOfKeyLength(String text) {
    assertThat(DevicePublicKey.parse(text)).isEmpty();
  }

  @Test
  void rejectsNullAndStandardBase64Alphabet() {
    assertThat(DevicePublicKey.parse(null)).isEmpty();
    assertThat(DevicePublicKey.parse("11qYAYKxCrfVS+7TyWQHOg7hcvPapiMlrwIaaPcHURo")).isEmpty();
  }

  @Test
  void rejectsANonCanonicalEncodingWithSameDecodedBytes() {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    String valid = encode(hex(RFC_KEY_HEX));
    char last = valid.charAt(42);
    String alternative = valid.substring(0, 42) + alphabet.charAt(alphabet.indexOf(last) ^ 1);

    assertThat(DevicePublicKey.parse(alternative)).isEmpty();
  }

  @Test
  void rejectsAValueAboveTheFieldPrime() {
    byte[] allOnes = new byte[32];
    java.util.Arrays.fill(allOnes, (byte) 0xff);
    allOnes[31] = 0x7f;

    assertThat(DevicePublicKey.parse(encode(allOnes))).isEmpty();
  }

  @Test
  void rejectsASetSignBitWhenXIsZero() {
    byte[] identity = new byte[32];
    identity[0] = 1;
    assertThat(DevicePublicKey.isCurvePoint(identity)).isTrue();

    byte[] signed = identity.clone();
    signed[31] |= (byte) 0x80;
    assertThat(DevicePublicKey.isCurvePoint(signed)).isFalse();
  }

  /**
   * Checks the curve equation with Euler's criterion, which is independent of the square root used
   * by the implementation: y is a point when (u / v) is a square or zero.
   */
  @Test
  void agreesWithEulerCriterionForRandomY() {
    Random random = new Random(42);
    BigInteger exponent = P.subtract(BigInteger.ONE).divide(BigInteger.TWO);
    int valid = 0;
    int invalid = 0;
    for (int i = 0; i < 400; i++) {
      byte[] raw = new byte[32];
      random.nextBytes(raw);
      raw[31] &= 0x7f;
      BigInteger y = new BigInteger(1, reverse(raw));
      if (y.compareTo(P) >= 0) {
        continue;
      }
      BigInteger y2 = y.multiply(y).mod(P);
      BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
      BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
      boolean expected =
          u.signum() == 0
              || u.multiply(v.modPow(P.subtract(BigInteger.TWO), P))
                  .mod(P)
                  .modPow(exponent, P)
                  .equals(BigInteger.ONE);
      assertThat(DevicePublicKey.isCurvePoint(raw)).as("y=%s", y).isEqualTo(expected);
      if (expected) {
        valid++;
      } else {
        invalid++;
      }
    }
    assertThat(valid).isPositive();
    assertThat(invalid).isPositive();
  }

  private static byte[] reverse(byte[] bytes) {
    byte[] out = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      out[i] = bytes[bytes.length - 1 - i];
    }
    return out;
  }
}
