package co.caudal.application.incident;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * A person of the aqueduct registers damage from the panel (docs/API.md, section 12.2; HU-27).
 *
 * @param caller who registers it
 * @param categoryCode a code of {@code DAMAGE_CATEGORY}
 * @param sectorId the sector, or null
 * @param description what happened, 10 to 500 characters
 * @param locationHint where, up to 120 characters, or null
 */
public record CreateIncidentCommand(
    Caller caller, String categoryCode, UUID sectorId, String description, String locationHint)
    implements AuditableCommand<IncidentView> {

  @Override
  public String auditEntityType() {
    return "incident";
  }

  @Override
  public String auditEntityId(IncidentView result) {
    return result.id().toString();
  }
}
