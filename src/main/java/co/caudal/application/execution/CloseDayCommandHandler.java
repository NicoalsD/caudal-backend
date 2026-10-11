package co.caudal.application.execution;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.ClosurePort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.domain.execution.DayState;
import co.caudal.domain.schedule.ProposalState;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.text.SafeText;

/**
 * Closes a day of service (docs/Reglas-de-negocio.md, section 14): only a day with a published
 * schedule closes, only once ({@link DayState}), and its proposal moves to {@code CLOSED}, so no
 * more executions are recorded for it. Two closures at the same time meet the unique key of the
 * database, which also answers CONFLICT.
 *
 * @pattern P13 Command
 */
public class CloseDayCommandHandler implements CommandHandler<CloseDayCommand, ClosureView> {

  private static final String PUBLISHED = "PUBLISHED";

  private final ClosurePort closures;
  private final ProposalPort proposals;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param closures closures
   * @param proposals proposals
   * @param unitOfWork transaction boundary
   */
  public CloseDayCommandHandler(
      ClosurePort closures, ProposalPort proposals, UnitOfWorkPort unitOfWork) {
    this.closures = closures;
    this.proposals = proposals;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ClosureView handle(CloseDayCommand command) {
    String notes =
        SafeText.optionalNote(
            "notes", command.notes(), FieldLimits.NOTE_MAX, FieldLimits.NOTE_MAX_LINES);
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          DayState.of(closures.find(command.serviceDate()).isPresent()).close();
          ProposalSummary published =
              proposals
                  .list(command.serviceDate(), command.serviceDate(), PUBLISHED, null, 1)
                  .stream()
                  .findFirst()
                  .orElseThrow(() -> new DomainException(ErrorCode.INVALID_STATE_TRANSITION));
          ProposalState closed = ProposalState.of(published.status()).close();
          if (!proposals.transition(published.id(), published.lockVersion(), closed.code())) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          return closures.insert(
              command.caller().aqueductId(),
              command.serviceDate(),
              notes,
              command.caller().userId());
        });
  }

  @Override
  public Class<CloseDayCommand> commandType() {
    return CloseDayCommand.class;
  }
}
