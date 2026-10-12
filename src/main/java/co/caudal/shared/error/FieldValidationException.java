package co.caudal.shared.error;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * One field of the request does not meet its rule, found by a check that needs more than a
 * constraint annotation (normalization first, or a lookup). It is reported like any other input
 * error: {@code 400 VALIDATION_ERROR} with the field and a field level code, never the value.
 */
public class FieldValidationException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param field name of the JSON field
   * @param fieldCode field level code, for example {@code FIELD_FORMAT_INVALID}
   */
  public FieldValidationException(String field, String fieldCode) {
    super(
        ErrorCode.VALIDATION_ERROR,
        Map.of("fields", List.of(Map.of("field", field, "code", fieldCode))));
  }
}
