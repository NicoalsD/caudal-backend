package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Adds a valve to a sector (docs/API.md, section 5.7; HU-14).
 *
 * @param caller the Board administrator
 * @param sectorId the sector
 * @param code short code, unique in the sector
 * @param name name
 * @param locationHint where to find it, or null
 * @param motorized true if a device can move it, or null for false
 * @param failSafePosition {@code KEEP}, {@code OPEN} or {@code CLOSED}, or null for {@code KEEP}
 */
public record CreateValveCommand(
    Caller caller,
    UUID sectorId,
    String code,
    String name,
    String locationHint,
    Boolean motorized,
    String failSafePosition)
    implements AuditableCommand<ValveView> {

  @Override
  public String auditEntityType() {
    return "valve";
  }

  @Override
  public String auditEntityId(ValveView result) {
    return result.id().toString();
  }
}
