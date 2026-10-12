package co.caudal.application.incident;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.IncidentPort;
import co.caudal.application.port.out.PermissionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.incident.IncidentState;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.text.SafeText;
import java.util.Set;

/**
 * Moves an incident with the rules of {@link IncidentState}. The transition is checked first, then
 * the permission to dismiss, then the note: dismissing needs {@code INCIDENT_MANAGE} (board only)
 * and a reason of 10 to 500 characters. Advancing the incident needs only the permission of the
 * endpoint, so the operator can verify, confirm and resolve. Each change adds a row to the history
 * of the incident.
 *
 * @pattern P18 State
 */
public class UpdateIncidentStatusCommandHandler
    implements CommandHandler<UpdateIncidentStatusCommand, IncidentView> {

  private static final String DISMISSED = "DISMISSED";
  private static final String DISMISS_PERMISSION = "INCIDENT_MANAGE";

  private final IncidentPort incidents;
  private final PermissionPort permissions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param incidents incidents
   * @param permissions permission matrix, to know who may dismiss
   * @param unitOfWork transaction boundary
   */
  public UpdateIncidentStatusCommandHandler(
      IncidentPort incidents, PermissionPort permissions, UnitOfWorkPort unitOfWork) {
    this.incidents = incidents;
    this.permissions = permissions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public IncidentView handle(UpdateIncidentStatusCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          IncidentView current =
              incidents
                  .find(command.incidentId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          IncidentState next = IncidentState.of(current.status()).moveTo(command.status());
          if (DISMISSED.equals(next.code()) && !mayDismiss(command.caller().role())) {
            throw new DomainException(ErrorCode.FORBIDDEN);
          }
          String note =
              SafeText.optionalNote(
                  "note", command.note(), FieldLimits.NOTE_MAX, FieldLimits.NOTE_MAX_LINES);
          if (DISMISSED.equals(next.code())
              && (note == null
                  || note.length() < FieldLimits.REASON_MIN
                  || note.length() > FieldLimits.REASON_MAX)) {
            throw new DomainException(ErrorCode.REASON_REQUIRED);
          }
          if (!incidents.changeStatus(
              current.id(), current.status(), next.code(), note, command.caller().userId())) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          return incidents.find(current.id()).orElseThrow();
        });
  }

  private boolean mayDismiss(String role) {
    Set<String> granted = permissions.findPermissionCodes(role);
    return granted.contains(DISMISS_PERMISSION);
  }

  @Override
  public Class<UpdateIncidentStatusCommand> commandType() {
    return UpdateIncidentStatusCommand.class;
  }
}
