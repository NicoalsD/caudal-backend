package co.caudal.domain.mfa;

import co.caudal.shared.FieldLimits;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238 over HOTP, RFC 4226): HMAC-SHA1, {@link
 * FieldLimits#TOTP_DIGITS} digits and steps of {@link FieldLimits#TOTP_STEP_SECONDS} seconds.
 *
 * <p>A code is accepted from the current step or {@link FieldLimits#TOTP_ALLOWED_DRIFT_STEPS} steps
 * around it, and only if its step is later than the last one used: the same code never works twice
 * (docs/Seguridad.md, section 2.4, anti-repetition with {@code last_used_step}).
 */
public final class Totp {

  private static final String ALGORITHM = "HmacSHA1";
  private static final Pattern CODE = Pattern.compile(FieldLimits.TOTP_CODE_PATTERN);
  private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};
  private static final int OFFSET_MASK = 0x0F;
  private static final int SIGN_MASK = 0x7F_FF_FF_FF;

  private Totp() {}

  /**
   * The step that contains a moment.
   *
   * @param moment the moment
   * @return the number of whole steps since the Unix epoch
   */
  public static long stepAt(Instant moment) {
    return Math.floorDiv(moment.getEpochSecond(), FieldLimits.TOTP_STEP_SECONDS);
  }

  /**
   * The code of a step, padded with zeros.
   *
   * @param secret the shared secret
   * @param step the step
   * @return the code
   */
  public static String codeAt(TotpSecret secret, long step) {
    byte[] hash = hmac(secret.bytes(), ByteBuffer.allocate(Long.BYTES).putLong(step).array());
    int offset = hash[hash.length - 1] & OFFSET_MASK;
    int binary = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & SIGN_MASK;
    String code = Integer.toString(binary % POWERS[FieldLimits.TOTP_DIGITS]);
    return "0".repeat(FieldLimits.TOTP_DIGITS - code.length()) + code;
  }

  /**
   * Checks a typed code.
   *
   * @param secret the shared secret
   * @param typed the code the person typed
   * @param now the current moment
   * @param lastUsedStep the step of the last accepted code, or null if none
   * @return the step the code belongs to, or empty if it is wrong, too old or already used
   */
  public static OptionalLong verify(
      TotpSecret secret, String typed, Instant now, Long lastUsedStep) {
    if (typed == null || !CODE.matcher(typed).matches()) {
      return OptionalLong.empty();
    }
    long current = stepAt(now);
    byte[] typedBytes = typed.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    long matched = Long.MIN_VALUE;
    for (long step = current - FieldLimits.TOTP_ALLOWED_DRIFT_STEPS;
        step <= current + FieldLimits.TOTP_ALLOWED_DRIFT_STEPS;
        step++) {
      byte[] expected = codeAt(secret, step).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      // Every step is compared, in constant time, so the timing does not reveal which one matched.
      if (MessageDigest.isEqual(expected, typedBytes) && matched == Long.MIN_VALUE) {
        matched = step;
      }
    }
    if (matched == Long.MIN_VALUE || (lastUsedStep != null && matched <= lastUsedStep)) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(matched);
  }

  private static byte[] hmac(byte[] key, byte[] message) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key, ALGORITHM));
      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
      throw new IllegalStateException("HmacSHA1 is not available", impossible);
    }
  }
}
