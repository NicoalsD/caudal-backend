package co.caudal.shared.error;

import java.util.Locale;

/**
 * Catalog of API error codes (docs/API.md, section 1.4).
 *
 * <p>The client decides its behavior from the code, never from the text. The Spanish message for
 * each code lives in {@code messages_es.properties} under {@link #messageKey()}, and the HTTP
 * status is assigned by the API layer.
 */
public enum ErrorCode {
  VALIDATION_ERROR,
  UNAUTHORIZED,
  INVALID_CREDENTIALS,
  SESSION_REVOKED,
  INVALID_SIGNATURE,
  NONCE_REPLAY,
  TIMESTAMP_OUT_OF_WINDOW,
  FORBIDDEN,
  PASSWORD_CHANGE_REQUIRED,
  PRIVACY_NOTICE_PENDING,
  DEVICE_INACTIVE,
  DEMO_ONLY,
  NOT_FOUND,
  CONFLICT,
  INVALID_STATE_TRANSITION,
  DUPLICATE_READING,
  SCHEDULE_OVERLAP,
  RULE_SET_IMMUTABLE,
  RATE_LIMITED,
  GAUGE_OUT_OF_RANGE,
  MISSING_TIMESTAMP,
  FUTURE_TIMESTAMP,
  TOO_OLD,
  REASON_REQUIRED,
  PASSWORD_POLICY_VIOLATION,
  RULE_SET_INVALID,
  NO_LEVEL_DATA,
  HOURS_EXCEEDED,
  SHIFT_LENGTH_INVALID,
  OUTSIDE_OPERATING_WINDOW,
  IMPORT_LIMIT_EXCEEDED,
  INVALID_KEY,
  CURSOR_INVALID,
  PAYLOAD_TOO_LARGE,
  UNSUPPORTED_MEDIA_TYPE,
  IA_UNAVAILABLE,
  INTERNAL_ERROR;

  /**
   * Returns the key of the user facing message in {@code messages_es.properties}.
   *
   * @return the message key, for example {@code error.gauge_out_of_range}
   */
  public String messageKey() {
    return "error." + name().toLowerCase(Locale.ROOT);
  }
}
