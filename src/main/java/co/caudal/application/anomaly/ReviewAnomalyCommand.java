package co.caudal.application.anomaly;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * A person confirms or dismisses an anomaly (docs/API.md, section 9.2; HU-17).
 *
 * @param caller a member of the Board
 * @param anomalyId the anomaly
 * @param status {@code CONFIRMED} or {@code DISMISSED}
 * @param resolutionNote the note, required to dismiss
 * @param relatedIncidentId the incident it led to, or null
 */
public record ReviewAnomalyCommand(
    Caller caller, UUID anomalyId, String status, String resolutionNote, UUID relatedIncidentId)
    implements AuditableCommand<AnomalyView> {

  @Override
  public String auditEntityType() {
    return "anomaly";
  }

  @Override
  public String auditEntityId(AnomalyView result) {
    return anomalyId.toString();
  }
}
