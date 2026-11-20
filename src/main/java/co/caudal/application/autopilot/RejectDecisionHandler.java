package co.caudal.application.autopilot;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.DecisionStatus;
import co.caudal.domain.event.OperatorActionRecorded;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;

/**
 * Rejects a pending decision with an optional reason (HU-41). It also counts as a human action that
 * pauses the autopilot.
 *
 * @pattern P13 Command
 */
public final class RejectDecisionHandler
    implements CommandHandler<RejectDecisionCommand, DecisionView> {

  private final AutopilotDecisionPort decisions;
  private final DomainEventPublisher events;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param decisions the decisions
   * @param events tells the autopilot that a person acted
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RejectDecisionHandler(
      AutopilotDecisionPort decisions,
      DomainEventPublisher events,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.decisions = decisions;
    this.events = events;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public DecisionView handle(RejectDecisionCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          DecisionView decision =
              decisions
                  .find(command.decisionId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          DecisionStatus.of(decision.status()).moveTo(DecisionStatus.REJECTED);
          Instant now = clock.instant();
          String detail =
              command.reason() == null || command.reason().isBlank()
                  ? null
                  : command.reason().strip();
          if (!decisions.transition(
              decision.id(),
              DecisionStatus.PENDING,
              DecisionStatus.REJECTED,
              command.caller().userId(),
              detail,
              now)) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
          }
          events.publish(
              new OperatorActionRecorded(
                  command.caller().aqueductId(), command.caller().userId(), now));
          return decisions.find(decision.id()).orElseThrow();
        });
  }

  @Override
  public Class<RejectDecisionCommand> commandType() {
    return RejectDecisionCommand.class;
  }
}
