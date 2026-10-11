package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Adds a sector to the network of the aqueduct (docs/API.md, section 5.4; HU-14).
 *
 * @param caller the Board administrator
 * @param code short code, unique in the aqueduct
 * @param name name
 * @param parentSectorId the sector it belongs to, or null
 * @param householdsCount households it serves, or null for zero
 */
public record CreateSectorCommand(
    Caller caller, String code, String name, UUID parentSectorId, Integer householdsCount)
    implements AuditableCommand<SectorView> {

  @Override
  public String auditEntityType() {
    return "sector";
  }

  @Override
  public String auditEntityId(SectorView result) {
    return result.id().toString();
  }
}
