package co.caudal.api.error;

/** Field level validation codes sent inside {@code error.details.fields}. */
public enum FieldErrorCode {
  FIELD_REQUIRED,
  FIELD_TOO_SHORT,
  FIELD_TOO_LONG,
  FIELD_FORMAT_INVALID,
  FIELD_CHARACTERS_NOT_ALLOWED,
  FIELD_VALUE_NOT_ALLOWED,
  FIELD_OUT_OF_RANGE,
  FIELD_TOO_MANY_LINES,
  FIELD_TOO_MANY_ITEMS
}
