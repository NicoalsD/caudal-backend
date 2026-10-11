package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Changes a valve (docs/API.md, section 5.8). The code never changes; a null field keeps its value.
 *
 * @param caller the Board administrator
 * @param valveId the valve
 * @param name new name
 * @param locationHint new hint
 * @param motorized new motorized flag
 * @param failSafePosition new fail-safe position
 * @param active new state
 */
public record UpdateValveCommand(
    Caller caller,
    UUID valveId,
    String name,
    String locationHint,
    Boolean motorized,
    String failSafePosition,
    Boolean active)
    implements AuditableCommand<ValveView> {

  @Override
  public String auditEntityType() {
    return "valve";
  }

  @Override
  public String auditEntityId(ValveView result) {
    return valveId.toString();
  }
}
