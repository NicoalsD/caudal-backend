package co.caudal.domain.rules;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Why the Board changes something (docs/Reglas-de-negocio.md, section 7): between {@link
 * FieldLimits#REASON_MIN} and {@link FieldLimits#REASON_MAX} characters after trimming.
 *
 * @param value the trimmed text
 */
public record ChangeReason(String value) {

  /**
   * Reads a reason typed by a person.
   *
   * @param typed the text, possibly null
   * @return the reason
   * @throws DomainException with {@code REASON_REQUIRED} if it is missing, too short or too long
   */
  public static ChangeReason require(String typed) {
    String value = typed == null ? "" : typed.strip();
    if (value.length() < FieldLimits.REASON_MIN || value.length() > FieldLimits.REASON_MAX) {
      throw new DomainException(
          ErrorCode.REASON_REQUIRED,
          Map.of("min", FieldLimits.REASON_MIN, "max", FieldLimits.REASON_MAX));
    }
    return new ChangeReason(value);
  }
}
