package co.caudal.application.device.auth;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;

/**
 * Step 1: the device exists and is ACTIVE. An unknown device gets the same answer as a bad
 * signature, so that the identifiers of the devices are not revealed.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class ActiveDeviceHandler extends DeviceAuthHandler {

  /** Reason of the event for an unknown device, the same as a bad signature. */
  static final String BAD_SIGNATURE = "BAD_SIGNATURE";

  /** Reason of the event for a suspended or retired device. */
  static final String DEVICE_INACTIVE = "DEVICE_INACTIVE";

  private static final String ACTIVE = "ACTIVE";

  @Override
  protected void check(DeviceAuthContext context) {
    DeviceCredentials device = context.credentials();
    if (device == null) {
      throw new DeviceAuthFailure(
          ErrorCode.INVALID_SIGNATURE,
          SecurityEventType.INVALID_SIGNATURE,
          Severity.MEDIUM,
          BAD_SIGNATURE);
    }
    if (!ACTIVE.equals(device.status())) {
      throw new DeviceAuthFailure(
          ErrorCode.DEVICE_INACTIVE,
          SecurityEventType.PERMISSION_DENIED,
          Severity.MEDIUM,
          DEVICE_INACTIVE);
    }
  }
}
