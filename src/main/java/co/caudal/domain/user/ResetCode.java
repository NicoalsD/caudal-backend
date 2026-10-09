package co.caudal.domain.user;

import co.caudal.domain.session.OpaqueToken;
import co.caudal.shared.FieldLimits;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One-time code that the Board gives to a person to set a password: at account creation (the
 * activation code) or when the password was forgotten. It is 12 Crockford base32 characters (60
 * bits), shown once as {@code ABCD-EFGH-JKMN}; the database keeps only its SHA-256.
 *
 * <p>People type it by hand, so {@link #parse(String)} accepts lower case and ignores spaces and
 * hyphens. The alphabet has no {@code I}, {@code L}, {@code O} or {@code U}, so it cannot be
 * misread.
 *
 * @param value the 12 characters, upper case, without separators
 */
public record ResetCode(String value) {

  private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final Pattern FORMAT = Pattern.compile(FieldLimits.RESET_CODE_PATTERN);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int GROUP_SIZE = 4;
  private static final String GROUP_SEPARATOR = "-";

  /**
   * Validates the canonical form.
   *
   * @throws IllegalArgumentException if the value is not 12 characters of the alphabet
   */
  public ResetCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a reset code");
    }
  }

  /**
   * Generates a new random code.
   *
   * @return a code with 60 bits of entropy
   */
  public static ResetCode generate() {
    StringBuilder code = new StringBuilder(FieldLimits.RESET_CODE_LENGTH);
    for (int i = 0; i < FieldLimits.RESET_CODE_LENGTH; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return new ResetCode(code.toString());
  }

  /**
   * Reads a code typed by a person.
   *
   * @param typed the text, possibly null
   * @return the code, or empty when the text cannot be one
   */
  public static Optional<ResetCode> parse(String typed) {
    if (typed == null || typed.length() > FieldLimits.RESET_CODE_INPUT_MAX) {
      return Optional.empty();
    }
    String canonical =
        typed
            .strip()
            .replace(GROUP_SEPARATOR, "")
            .replace(" ", "")
            .toUpperCase(java.util.Locale.ROOT);
    return FORMAT.matcher(canonical).matches()
        ? Optional.of(new ResetCode(canonical))
        : Optional.empty();
  }

  /**
   * The text shown to the person, in groups of four.
   *
   * @return for example {@code ABCD-EFGH-JKMN}
   */
  public String display() {
    StringBuilder text = new StringBuilder(value.length() + value.length() / GROUP_SIZE);
    for (int i = 0; i < value.length(); i += GROUP_SIZE) {
      if (i > 0) {
        text.append(GROUP_SEPARATOR);
      }
      text.append(value, i, i + GROUP_SIZE);
    }
    return text.toString();
  }

  /**
   * The value to store.
   *
   * @return lower case hexadecimal SHA-256 of the code (a 60 bit random code needs no slow hash)
   */
  public String hash() {
    return OpaqueToken.hashOf(value);
  }

  @Override
  public String toString() {
    return "ResetCode[REDACTED]";
  }
}
