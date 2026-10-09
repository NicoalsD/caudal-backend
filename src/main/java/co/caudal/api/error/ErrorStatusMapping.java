package co.caudal.api.error;

import co.caudal.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Assigns the HTTP status of every {@link ErrorCode} (docs/API.md, section 1.4). */
public final class ErrorStatusMapping {

  private ErrorStatusMapping() {}

  /**
   * Returns the HTTP status of an error code.
   *
   * @param code the error code
   * @return its status
   */
  public static HttpStatus statusOf(ErrorCode code) {
    return switch (code) {
      case VALIDATION_ERROR, CURSOR_INVALID -> HttpStatus.BAD_REQUEST;
      case UNAUTHORIZED,
          INVALID_CREDENTIALS,
          SESSION_REVOKED,
          INVALID_SIGNATURE,
          NONCE_REPLAY,
          TIMESTAMP_OUT_OF_WINDOW ->
          HttpStatus.UNAUTHORIZED;
      case FORBIDDEN,
          PASSWORD_CHANGE_REQUIRED,
          PRIVACY_NOTICE_PENDING,
          DEVICE_INACTIVE,
          DEMO_ONLY ->
          HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT,
          INVALID_STATE_TRANSITION,
          DUPLICATE_READING,
          SCHEDULE_OVERLAP,
          RULE_SET_IMMUTABLE ->
          HttpStatus.CONFLICT;
      case PAYLOAD_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
      case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case GAUGE_OUT_OF_RANGE,
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
          INVALID_KEY ->
          HttpStatus.UNPROCESSABLE_CONTENT;
      case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case IA_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
