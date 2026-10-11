package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Changes a sector (docs/API.md, section 5.5). The code never changes.
 *
 * @param caller the Board administrator
 * @param sectorId the sector
 * @param name new name, or null
 * @param householdsCount new number of households, or null
 * @param active new state, or null
 */
public record UpdateSectorCommand(
    Caller caller, UUID sectorId, String name, Integer householdsCount, Boolean active)
    implements AuditableCommand<SectorView> {

  @Override
  public String auditEntityType() {
    return "sector";
  }

  @Override
  public String auditEntityId(SectorView result) {
    return sectorId.toString();
  }
}
