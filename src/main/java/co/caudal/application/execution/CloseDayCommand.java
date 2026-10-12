package co.caudal.application.execution;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.LocalDate;

/**
 * The plumber or the Board closes a day of service (docs/API.md, section 11.3; HU-29).
 *
 * @param caller who closes it
 * @param serviceDate the day
 * @param notes the notes, or null
 */
public record CloseDayCommand(Caller caller, LocalDate serviceDate, String notes)
    implements AuditableCommand<ClosureView> {

  @Override
  public String auditEntityType() {
    return "day_closure";
  }

  @Override
  public String auditEntityId(ClosureView result) {
    return result.id().toString();
  }
}
