package co.caudal.domain.user;

import co.caudal.shared.FieldLimits;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Username of an account: 3 to 32 characters, lower case ASCII, starting and ending with a letter
 * or a digit ({@link FieldLimits#USERNAME_PATTERN}).
 *
 * <p>{@link #parse(String)} normalizes first (trim and lower case for ASCII letters only) and then
 * validates, so {@code " Junta.Presidencia "} and {@code "junta.presidencia"} are the same account.
 * The canonical value is the only form that is stored, hashed for logging or compared.
 *
 * @param value the normalized username
 */
public record Username(String value) {

  private static final Pattern FORMAT = Pattern.compile(FieldLimits.USERNAME_PATTERN);
  private static final char UPPER_A = 'A';
  private static final char UPPER_Z = 'Z';
  private static final int CASE_OFFSET = 'a' - 'A';

  /**
   * Validates the canonical form.
   *
   * @throws InvalidUsernameException if the value does not match the username pattern
   */
  public Username {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new InvalidUsernameException();
    }
  }

  /**
   * Normalizes and validates a raw text.
   *
   * @param raw text typed by the person
   * @return the username
   * @throws InvalidUsernameException if the normalized text is not a valid username
   */
  public static Username parse(String raw) {
    return new Username(normalize(raw));
  }

  /**
   * Normalizes and validates a raw text without throwing.
   *
   * @param raw text typed by the person, possibly null
   * @return the username, or empty when the text is not a valid one
   */
  public static Optional<Username> tryParse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalized = normalize(raw);
    return FORMAT.matcher(normalized).matches()
        ? Optional.of(new Username(normalized))
        : Optional.empty();
  }

  /**
   * Trims the text and lowers the ASCII letters. Other characters are kept so that they fail the
   * ASCII pattern instead of being silently mapped to a different account.
   *
   * @param raw text typed by the person
   * @return the normalized text, not yet validated
   */
  public static String normalize(String raw) {
    String trimmed = Objects.requireNonNull(raw, "raw").strip();
    StringBuilder lower = new StringBuilder(trimmed.length());
    for (int i = 0; i < trimmed.length(); i++) {
      char current = trimmed.charAt(i);
      lower.append(
          current >= UPPER_A && current <= UPPER_Z ? (char) (current + CASE_OFFSET) : current);
    }
    return lower.toString();
  }

  @Override
  public String toString() {
    return value;
  }
}
