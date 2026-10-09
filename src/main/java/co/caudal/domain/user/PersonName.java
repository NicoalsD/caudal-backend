package co.caudal.domain.user;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.FieldValidationException;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Full name of a person: 2 to 80 characters made of letters, marks, spaces, dots, apostrophes and
 * hyphens, starting with a letter ({@link FieldLimits#PERSON_NAME_PATTERN}).
 *
 * <p>{@link #parse(String)} normalizes first (Unicode NFC, trimmed, inner runs of white space
 * collapsed to one space) and then validates, so the stored name is always in one form. Control,
 * bidirectional and zero width characters are not letters, so the pattern rejects them.
 *
 * @param value the normalized name
 */
public record PersonName(String value) {

  private static final Pattern FORMAT = Pattern.compile(FieldLimits.PERSON_NAME_PATTERN);
  private static final String FIELD = "full_name";

  /**
   * Trims the ends, collapses inner white space to one space and validates. Use {@link
   * #parse(String)} to also apply Unicode NFC.
   *
   * @throws FieldValidationException if the value is too short, too long or has forbidden
   *     characters
   */
  public PersonName {
    value = collapseWhiteSpace(Objects.requireNonNull(value, "value"));
    int length = value.codePointCount(0, value.length());
    if (length < FieldLimits.PERSON_NAME_MIN) {
      throw new FieldValidationException(FIELD, "FIELD_TOO_SHORT");
    }
    if (length > FieldLimits.PERSON_NAME_MAX) {
      throw new FieldValidationException(FIELD, "FIELD_TOO_LONG");
    }
    if (!FORMAT.matcher(value).matches()) {
      throw new FieldValidationException(FIELD, "FIELD_CHARACTERS_NOT_ALLOWED");
    }
  }

  /**
   * Normalizes and validates a name typed by a person.
   *
   * @param raw the text
   * @return the name
   * @throws FieldValidationException if the normalized text is not a valid name
   */
  public static PersonName parse(String raw) {
    return new PersonName(
        Normalizer.normalize(Objects.requireNonNull(raw, "raw"), Normalizer.Form.NFC));
  }

  /** Trims the ends and turns every run of white space into one space. */
  private static String collapseWhiteSpace(String text) {
    StringBuilder collapsed = new StringBuilder(text.length());
    boolean pendingSpace = false;
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      index += Character.charCount(codePoint);
      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
        pendingSpace = !collapsed.isEmpty();
      } else {
        if (pendingSpace) {
          collapsed.append(' ');
          pendingSpace = false;
        }
        collapsed.appendCodePoint(codePoint);
      }
    }
    return collapsed.toString();
  }

  @Override
  public String toString() {
    return "PersonName[REDACTED]";
  }
}
