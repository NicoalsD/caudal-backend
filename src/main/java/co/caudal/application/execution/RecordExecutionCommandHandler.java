package co.caudal.application.execution;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.ExecutionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.execution.ExecutionTimes;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import co.caudal.shared.text.SafeText;
import java.util.Map;

/**
 * Records the execution of a shift of a published schedule. An execution is never edited: a change
 * is a new row that points to the one it replaces, and each execution is replaced at most once. A
 * closed day takes no more executions, because its proposal is no longer {@code PUBLISHED}.
 *
 * @pattern P13 Command
 */
public class RecordExecutionCommandHandler
    implements CommandHandler<RecordExecutionCommand, ExecutionView> {

  private static final String PUBLISHED = "PUBLISHED";

  private final ExecutionPort executions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param executions executions
   * @param unitOfWork transaction boundary
   */
  public RecordExecutionCommandHandler(ExecutionPort executions, UnitOfWorkPort unitOfWork) {
    this.executions = executions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ExecutionView handle(RecordExecutionCommand command) {
    String note =
        SafeText.optionalNote(
            "note", command.note(), FieldLimits.NOTE_MAX, FieldLimits.NOTE_MAX_LINES);
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ShiftContext shift =
              executions
                  .shift(command.itemId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (!PUBLISHED.equals(shift.proposalStatus())) {
            throw new DomainException(
                ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", shift.proposalStatus()));
          }
          checkReplacement(command);
          ExecutionTimes times =
              ExecutionTimes.resolve(
                  command.status(),
                  command.actualStart(),
                  command.actualEnd(),
                  shift.startAt(),
                  shift.endAt());
          return executions.insert(
              command.caller().aqueductId(),
              new NewExecution(
                  command.itemId(),
                  command.status(),
                  times.start(),
                  times.end(),
                  note,
                  command.supersedesId(),
                  command.caller().userId()));
        });
  }

  /** A first execution needs no reference; a later one replaces the current one, once. */
  private void checkReplacement(RecordExecutionCommand command) {
    if (command.supersedesId() == null) {
      if (executions.hasCurrent(command.itemId())) {
        throw new DomainException(ErrorCode.CONFLICT);
      }
      return;
    }
    ExecutionView previous =
        executions
            .find(command.supersedesId())
            .filter(e -> e.scheduleItemId().equals(command.itemId()))
            .orElseThrow(
                () -> new FieldValidationException("supersedes_id", "FIELD_VALUE_NOT_ALLOWED"));
    if (executions.superseded(previous.id())) {
      throw new DomainException(ErrorCode.CONFLICT);
    }
  }

  @Override
  public Class<RecordExecutionCommand> commandType() {
    return RecordExecutionCommand.class;
  }
}
