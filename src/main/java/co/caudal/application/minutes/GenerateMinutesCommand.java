package co.caudal.application.minutes;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.LocalDate;

/**
 * The Board generates the minutes of a period (docs/API.md, section 13.1; HU-30).
 *
 * @param caller a member of the Board
 * @param periodFrom first day
 * @param periodTo last day
 */
public record GenerateMinutesCommand(Caller caller, LocalDate periodFrom, LocalDate periodTo)
    implements AuditableCommand<MinutesView> {

  @Override
  public String auditEntityType() {
    return "minutes";
  }

  @Override
  public String auditEntityId(MinutesView result) {
    return result.id().toString();
  }
}
