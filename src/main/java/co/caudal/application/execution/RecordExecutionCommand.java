package co.caudal.application.execution;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.Instant;
import java.util.UUID;

/**
 * The plumber or the Board records what happened in a shift (docs/API.md, section 11.1; HU-29).
 *
 * @param caller who records it
 * @param itemId the shift
 * @param status COMPLETED, PARTIAL or NOT_EXECUTED
 * @param actualStart real start, or null
 * @param actualEnd real end, or null
 * @param note the note, or null
 * @param supersedesId the execution it replaces, or null
 */
public record RecordExecutionCommand(
    Caller caller,
    UUID itemId,
    String status,
    Instant actualStart,
    Instant actualEnd,
    String note,
    UUID supersedesId)
    implements AuditableCommand<ExecutionView> {

  @Override
  public String auditEntityType() {
    return "shift_execution";
  }

  @Override
  public String auditEntityId(ExecutionView result) {
    return result.id().toString();
  }
}
