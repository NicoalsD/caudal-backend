package co.caudal.application.device;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Revokes a key of a device without a replacement (docs/API.md, section 16.8).
 *
 * @param caller the administrator
 * @param deviceId the device
 * @param keyId the key
 */
public record RevokeDeviceKeyCommand(Caller caller, UUID deviceId, UUID keyId)
    implements AuditableCommand<DeviceKeyView> {

  @Override
  public String auditEntityType() {
    return "device_key";
  }

  @Override
  public String auditEntityId(DeviceKeyView result) {
    return keyId.toString();
  }
}
