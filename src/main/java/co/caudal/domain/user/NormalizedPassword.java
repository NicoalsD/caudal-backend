package co.caudal.domain.user;

import java.text.Normalizer;
import java.util.Objects;

/**
 * A password in Unicode NFKC form, the only form that is hashed, compared with the common list or
 * checked against the username.
 *
 * <p>NFKC makes the full width {@code Ｐａｓｓ１２３} and the ASCII {@code Pass123} the same password, and
 * the composed and decomposed forms of an accent the same too. The text is never trimmed: spaces
 * are part of the password. The value never appears in {@link #toString()}, so it cannot reach a
 * log by accident.
 *
 * @param value the NFKC text
 */
public record NormalizedPassword(String value) {

  private static final String REDACTED = "[REDACTED]";

  /**
   * Keeps the text as given; use {@link #of(String)} to normalize.
   *
   * @throws NullPointerException if the value is null
   */
  public NormalizedPassword {
    Objects.requireNonNull(value, "value");
  }

  /**
   * Normalizes a raw password with NFKC, without trimming.
   *
   * @param raw password typed by the person
   * @return the normalized password
   */
  public static NormalizedPassword of(String raw) {
    return new NormalizedPassword(
        Normalizer.normalize(Objects.requireNonNull(raw, "raw"), Normalizer.Form.NFKC));
  }

  /**
   * Counts Unicode code points, the unit of the length limits (an emoji counts as one).
   *
   * @return the number of code points
   */
  public int length() {
    return value.codePointCount(0, value.length());
  }

  @Override
  public String toString() {
    return REDACTED;
  }
}
