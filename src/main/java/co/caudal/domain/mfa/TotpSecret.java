package co.caudal.domain.mfa;

import co.caudal.shared.FieldLimits;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * The shared secret of a TOTP factor (RFC 6238). It is shown once, as Base32 and as an {@code
 * otpauth://} URI for the QR code, and stored only encrypted; {@link #toString()} never reveals it.
 */
public final class TotpSecret {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final byte[] bytes;

  private TotpSecret(byte[] bytes) {
    this.bytes = bytes.clone();
  }

  /**
   * Generates a new random secret.
   *
   * @return the secret
   */
  public static TotpSecret generate() {
    byte[] bytes = new byte[FieldLimits.TOTP_SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return new TotpSecret(bytes);
  }

  /**
   * Wraps a secret read back from storage.
   *
   * @param bytes the decrypted secret
   * @return the secret
   */
  public static TotpSecret of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length == 0) {
      throw new IllegalArgumentException("Empty TOTP secret");
    }
    return new TotpSecret(bytes);
  }

  /**
   * Reads a secret typed or scanned as Base32 (upper case, without padding).
   *
   * @param base32 the text
   * @return the secret
   * @throws IllegalArgumentException if the text is not Base32
   */
  public static TotpSecret fromBase32(String base32) {
    return of(Base32.decode(base32));
  }

  /**
   * The raw secret, to encrypt it or compute codes.
   *
   * @return a copy of the bytes
   */
  public byte[] bytes() {
    return bytes.clone();
  }

  /**
   * The secret as authenticator apps accept it when typed.
   *
   * @return Base32 without padding
   */
  public String base32() {
    return Base32.encode(bytes);
  }

  /**
   * The URI an authenticator app reads from a QR code.
   *
   * @param issuer name of the service shown in the app
   * @param account name of the account shown in the app
   * @return the {@code otpauth://totp/...} URI
   */
  public String otpauthUri(String issuer, String account) {
    String label = encode(issuer) + ":" + encode(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + base32()
        + "&issuer="
        + encode(issuer)
        + "&algorithm=SHA1&digits="
        + FieldLimits.TOTP_DIGITS
        + "&period="
        + FieldLimits.TOTP_STEP_SECONDS;
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TotpSecret that && Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "TotpSecret[REDACTED]";
  }
}
