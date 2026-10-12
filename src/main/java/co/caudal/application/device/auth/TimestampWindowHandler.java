package co.caudal.application.device.auth;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.ErrorCode;

/**
 * Step 3: the timestamp is within the allowed clock difference of the server, plus or minus {@link
 * FieldLimits#DEVICE_SIGNATURE_WINDOW_SECONDS} seconds.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class TimestampWindowHandler extends DeviceAuthHandler {

  /** Reason of the event for a timestamp out of the window. */
  static final String TIMESTAMP_OUT_OF_WINDOW = "TIMESTAMP_OUT_OF_WINDOW";

  @Override
  protected void check(DeviceAuthContext context) {
    long skew = Math.abs(context.now().getEpochSecond() - context.request().timestamp());
    if (skew > FieldLimits.DEVICE_SIGNATURE_WINDOW_SECONDS) {
      throw new DeviceAuthFailure(
          ErrorCode.TIMESTAMP_OUT_OF_WINDOW,
          SecurityEventType.INVALID_SIGNATURE,
          Severity.LOW,
          TIMESTAMP_OUT_OF_WINDOW);
    }
  }
}
