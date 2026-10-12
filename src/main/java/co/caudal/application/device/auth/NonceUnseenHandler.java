package co.caudal.application.device.auth;

import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;

/**
 * Step 4: the nonce was not seen for this device within its lifetime. It only looks: the nonce is
 * stored after the signature is verified (step 5b), so a request without a key cannot fill the
 * table.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class NonceUnseenHandler extends DeviceAuthHandler {

  @Override
  protected void check(DeviceAuthContext context) {
    if (context.nonceSeen()) {
      throw new DeviceAuthFailure(
          ErrorCode.NONCE_REPLAY, SecurityEventType.NONCE_REPLAY, Severity.HIGH, null);
    }
  }
}
