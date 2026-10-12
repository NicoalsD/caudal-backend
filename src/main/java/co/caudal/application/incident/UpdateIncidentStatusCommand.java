package co.caudal.application.incident;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * A person of the Board moves an incident to another status (docs/API.md, section 12.3).
 *
 * @param caller who moves it
 * @param incidentId the incident
 * @param status VERIFYING, CONFIRMED, RESOLVED or DISMISSED
 * @param note the note of the change, 0 to 500 characters; 10 to 500 to dismiss
 */
public record UpdateIncidentStatusCommand(
    Caller caller, UUID incidentId, String status, String note)
    implements AuditableCommand<IncidentView> {

  @Override
  public String auditEntityType() {
    return "incident";
  }

  @Override
  public String auditEntityId(IncidentView result) {
    return incidentId.toString();
  }
}
