package co.caudal.application.autopilot;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.DecisionStatus;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;

/**
 * Creates the valve command of a pending decision on behalf of the autopilot and marks the decision
 * {@code EXECUTED}, in one transaction: either both happen or neither. The command goes through the
 * command bus, so it is audited with the actor {@code SYSTEM}.
 *
 * @pattern P13 Command
 */
public final class ExecuteDecisionHandler
    implements CommandHandler<ExecuteDecisionCommand, DecisionView> {

  private final AutopilotDecisionPort decisions;
  private final AutopilotCommandFactory factory;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param decisions the decisions
   * @param factory creates the valve command
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ExecuteDecisionHandler(
      AutopilotDecisionPort decisions,
      AutopilotCommandFactory factory,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.decisions = decisions;
    this.factory = factory;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public DecisionView handle(ExecuteDecisionCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          DecisionView decision =
              decisions
                  .find(command.decisionId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          DecisionStatus.of(decision.status()).moveTo(DecisionStatus.EXECUTED);
          factory.create(command.caller().aqueductId(), decision, null);
          Instant now = clock.instant();
          if (!decisions.transition(
              decision.id(), DecisionStatus.PENDING, DecisionStatus.EXECUTED, null, null, now)) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
          }
          return decisions.find(decision.id()).orElseThrow();
        });
  }

  @Override
  public Class<ExecuteDecisionCommand> commandType() {
    return ExecuteDecisionCommand.class;
  }
}
