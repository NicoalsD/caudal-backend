package co.caudal.application.minutes;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board closes the minutes as final (docs/API.md, section 13.7; HU-30).
 *
 * @param caller a member of the Board
 * @param minutesId the minutes
 */
public record FinalizeMinutesCommand(Caller caller, UUID minutesId)
    implements AuditableCommand<MinutesView> {

  @Override
  public String auditEntityType() {
    return "minutes";
  }

  @Override
  public String auditEntityId(MinutesView result) {
    return minutesId.toString();
  }
}
