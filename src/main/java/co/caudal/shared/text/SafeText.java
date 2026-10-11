package co.caudal.shared.text;

import co.caudal.shared.error.FieldValidationException;
import java.text.Normalizer;

/**
 * Normalizes and checks free text typed by people (.agents/input-validation.md, section 1): NFC,
 * CRLF to LF, trimmed, and without control, bidi, zero-width or line separator characters. Long
 * texts may keep line breaks.
 */
public final class SafeText {

  private static final int C0_END = 0x1F;
  private static final int DEL = 0x7F;
  private static final int C1_END = 0x9F;
  private static final int ZERO_WIDTH_START = 0x200B;
  private static final int ZERO_WIDTH_END = 0x200F;
  private static final int LINE_SEPARATOR = 0x2028;
  private static final int PARAGRAPH_SEPARATOR = 0x2029;
  private static final int BIDI_START = 0x202A;
  private static final int BIDI_END = 0x202E;
  private static final int ISOLATE_START = 0x2066;
  private static final int ISOLATE_END = 0x2069;
  private static final int BOM = 0xFEFF;

  private SafeText() {}

  /**
   * A required text that may have line breaks, such as a description.
   *
   * @param field the JSON field, for the error
   * @param raw what was typed
   * @param min shortest length after trimming
   * @param max longest length after trimming
   * @return the normalized text
   * @throws FieldValidationException when it is missing, too short or long, or has a forbidden
   *     character
   */
  public static String longText(String field, String raw, int min, int max) {
    String text = normalize(raw);
    if (text.isEmpty()) {
      throw new FieldValidationException(field, "FIELD_REQUIRED");
    }
    check(field, text, true);
    length(field, text, min, max);
    return text;
  }

  /**
   * An optional text of one line, such as a place.
   *
   * @param field the JSON field, for the error
   * @param raw what was typed, or null
   * @param max longest length after trimming
   * @return the normalized text, or null when empty
   * @throws FieldValidationException when it is too long or has a forbidden character
   */
  public static String optionalLine(String field, String raw, int max) {
    String text = normalize(raw);
    if (text.isEmpty()) {
      return null;
    }
    check(field, text, false);
    length(field, text, 0, max);
    return text;
  }

  /**
   * An optional note that may have a few lines.
   *
   * @param field the JSON field, for the error
   * @param raw what was typed, or null
   * @param max longest length after trimming
   * @param maxLines most lines
   * @return the normalized note, or null when empty
   * @throws FieldValidationException when it is too long, has too many lines or a forbidden
   *     character
   */
  public static String optionalNote(String field, String raw, int max, int maxLines) {
    String text = normalize(raw);
    if (text.isEmpty()) {
      return null;
    }
    check(field, text, true);
    length(field, text, 0, max);
    if (text.lines().count() > maxLines) {
      throw new FieldValidationException(field, "FIELD_TOO_MANY_LINES");
    }
    return text;
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return Normalizer.normalize(raw, Normalizer.Form.NFC).replace("\r\n", "\n").strip();
  }

  private static void length(String field, String text, int min, int max) {
    int length = text.codePointCount(0, text.length());
    if (length < min) {
      throw new FieldValidationException(field, "FIELD_TOO_SHORT");
    }
    if (length > max) {
      throw new FieldValidationException(field, "FIELD_TOO_LONG");
    }
  }

  private static void check(String field, String text, boolean lineBreaks) {
    boolean bad = text.codePoints().anyMatch(c -> forbidden(c) && !(lineBreaks && c == '\n'));
    if (bad) {
      throw new FieldValidationException(field, "FIELD_CHARACTERS_NOT_ALLOWED");
    }
  }

  private static boolean forbidden(int c) {
    return c <= C0_END
        || (c >= DEL && c <= C1_END)
        || (c >= ZERO_WIDTH_START && c <= ZERO_WIDTH_END)
        || c == LINE_SEPARATOR
        || c == PARAGRAPH_SEPARATOR
        || (c >= BIDI_START && c <= BIDI_END)
        || (c >= ISOLATE_START && c <= ISOLATE_END)
        || c == BOM;
  }
}
