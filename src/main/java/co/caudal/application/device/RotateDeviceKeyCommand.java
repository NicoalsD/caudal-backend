package co.caudal.application.device;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Registers the public key of a device and rotates the previous one (docs/API.md, section 16.5).
 *
 * @param caller the administrator
 * @param deviceId the device
 * @param publicKey the key in base64url without padding, as sent
 */
public record RotateDeviceKeyCommand(Caller caller, UUID deviceId, String publicKey)
    implements AuditableCommand<DeviceKeyView> {

  @Override
  public String auditEntityType() {
    return "device_key";
  }

  @Override
  public String auditEntityId(DeviceKeyView result) {
    return result.keyId().toString();
  }
}
