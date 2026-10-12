package co.caudal.application.device.auth;

import co.caudal.domain.device.DevicePublicKey;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.error.ErrorCode;
import java.util.Optional;

/**
 * Step 5: the signature verifies with one of the valid keys of the device over the canonical
 * string. Every valid key is tried, because during a rotation two keys are valid.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class SignatureHandler extends DeviceAuthHandler {

  /** Reason of the event for a signature that verifies with no valid key. */
  static final String BAD_SIGNATURE = "BAD_SIGNATURE";

  @Override
  protected void check(DeviceAuthContext context) {
    SignedRequest request = context.request();
    byte[] canonical = request.canonical();
    for (DeviceKeyCredential key : context.credentials().keys()) {
      Optional<DevicePublicKey> publicKey = DevicePublicKey.parse(key.publicKey());
      if (publicKey.isPresent() && publicKey.get().verifies(canonical, request.signature())) {
        context.matchKey(key.keyId());
        return;
      }
    }
    throw new DeviceAuthFailure(
        ErrorCode.INVALID_SIGNATURE,
        SecurityEventType.INVALID_SIGNATURE,
        Severity.MEDIUM,
        BAD_SIGNATURE);
  }
}
