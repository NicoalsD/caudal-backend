package co.caudal.application.device;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Registers a field device (docs/API.md, section 16.4).
 *
 * @param caller the administrator
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name the name
 * @param tankId the tank of a level sensor, or null
 * @param valveId the valve of a valve actuator, or null
 * @param firmwareVersion firmware version, or null
 */
public record CreateDeviceCommand(
    Caller caller, String kind, String name, UUID tankId, UUID valveId, String firmwareVersion)
    implements AuditableCommand<DeviceView> {

  @Override
  public String auditEntityType() {
    return "device";
  }

  @Override
  public String auditEntityId(DeviceView result) {
    return result.id().toString();
  }
}
