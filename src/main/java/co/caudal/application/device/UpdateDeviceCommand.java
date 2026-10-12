package co.caudal.application.device;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Changes the status of a device (docs/API.md, section 16.7).
 *
 * @param caller the administrator
 * @param deviceId the device
 * @param status ACTIVE, SUSPENDED or RETIRED
 */
public record UpdateDeviceCommand(Caller caller, UUID deviceId, String status)
    implements AuditableCommand<DeviceView> {

  @Override
  public String auditEntityType() {
    return "device";
  }

  @Override
  public String auditEntityId(DeviceView result) {
    return deviceId.toString();
  }
}
