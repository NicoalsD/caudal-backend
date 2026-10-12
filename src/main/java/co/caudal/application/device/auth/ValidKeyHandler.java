package co.caudal.application.device.auth;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;

/**
 * Step 2: the device has at least one key that is still valid (revoked_at is null or in the
 * future).
 *
 * @pattern P12 Chain of Responsibility
 */
public final class ValidKeyHandler extends DeviceAuthHandler {

  /** Reason of the event for a device without a valid key. */
  static final String NO_ACTIVE_KEY = "NO_ACTIVE_KEY";

  @Override
  protected void check(DeviceAuthContext context) {
    if (context.credentials().keys().isEmpty()) {
      throw new DeviceAuthFailure(
          ErrorCode.INVALID_SIGNATURE,
          SecurityEventType.INVALID_SIGNATURE,
          Severity.MEDIUM,
          NO_ACTIVE_KEY);
    }
  }
}
