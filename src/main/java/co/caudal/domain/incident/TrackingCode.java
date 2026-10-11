package co.caudal.domain.incident;

import co.caudal.domain.session.OpaqueToken;
import co.caudal.shared.FieldLimits;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The code a person gets after reporting damage, to follow the report without an account
 * (docs/Reglas-de-negocio.md, section 15). It is {@link FieldLimits#TRACKING_CODE_LENGTH}
 * characters of Crockford base32; only its SHA-256 is stored, so the code is shown once.
 *
 * @param value the code, upper case
 */
public record TrackingCode(String value) {

  private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final Pattern FORMAT = Pattern.compile(FieldLimits.TRACKING_CODE_PATTERN);
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Validates the canonical form.
   *
   * @throws IllegalArgumentException if it is not a tracking code
   */
  public TrackingCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a tracking code");
    }
  }

  /**
   * Generates a new random code.
   *
   * @return the code
   */
  public static TrackingCode generate() {
    StringBuilder code = new StringBuilder(FieldLimits.TRACKING_CODE_LENGTH);
    for (int i = 0; i < FieldLimits.TRACKING_CODE_LENGTH; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return new TrackingCode(code.toString());
  }

  /**
   * Reads a code typed by a person.
   *
   * @param typed the text, possibly null
   * @return the code, or empty when the text cannot be one
   */
  public static Optional<TrackingCode> parse(String typed) {
    if (typed == null) {
      return Optional.empty();
    }
    String canonical = typed.strip().replace("-", "").toUpperCase(Locale.ROOT);
    return FORMAT.matcher(canonical).matches()
        ? Optional.of(new TrackingCode(canonical))
        : Optional.empty();
  }

  /**
   * The value to store.
   *
   * @return lower case hexadecimal SHA-256 of the code
   */
  public String hash() {
    return OpaqueToken.hashOf(value);
  }

  @Override
  public String toString() {
    return "TrackingCode[REDACTED]";
  }
}
