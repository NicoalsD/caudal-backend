package co.caudal.application.autopilot;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.application.port.out.AutopilotSettingsPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.autopilot.AutopilotAction;
import co.caudal.domain.autopilot.AutopilotSettings;
import co.caudal.domain.autopilot.CycleContext;
import co.caudal.domain.autopilot.DecisionStatus;
import co.caudal.domain.autopilot.ReserveFloorGuard;
import co.caudal.domain.event.OperatorActionRecorded;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Accepts a pending decision and, for an OPEN or a CLOSE, emits the valve command (HU-41). It only
 * works while the mode allows decisions to be accepted, and the reserve floor is checked again with
 * the level of now: an OPEN under the floor leaves the decision {@code SKIPPED} and answers {@code
 * CONFLICT}. Accepting counts as a human action, so it pauses the autopilot.
 *
 * @pattern P13 Command
 */
public final class AcceptDecisionHandler
    implements CommandHandler<AcceptDecisionCommand, DecisionView> {

  private final AutopilotDecisionPort decisions;
  private final AutopilotSettingsPort settings;
  private final AutopilotCommandFactory factory;
  private final SnapshotBuilder levels;
  private final DomainEventPublisher events;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param decisions the decisions
   * @param settings the autopilot settings
   * @param factory creates the valve command
   * @param levels reads the current level of the tank
   * @param events tells the autopilot that a person acted
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  // Each collaborator is one step of accepting; the handler is their only user.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public AcceptDecisionHandler(
      AutopilotDecisionPort decisions,
      AutopilotSettingsPort settings,
      AutopilotCommandFactory factory,
      SnapshotBuilder levels,
      DomainEventPublisher events,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.decisions = decisions;
    this.settings = settings;
    this.factory = factory;
    this.levels = levels;
    this.events = events;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public DecisionView handle(AcceptDecisionCommand command) {
    Result result = unitOfWork.executeAs(command.caller().scope(), () -> accept(command));
    // The skip is kept and then reported: a failure inside the transaction would undo it.
    if (result.blocked()) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("field", "reserve_floor_pct"));
    }
    return result.decision();
  }

  private Result accept(AcceptDecisionCommand command) {
    DecisionView decision =
        decisions
            .find(command.decisionId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    DecisionStatus.of(decision.status()).moveTo(DecisionStatus.ACCEPTED);
    AutopilotSettings current = settings.getOrCreate(command.caller().aqueductId());
    if (!current.mode().acceptsDecisions()) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("field", "mode"));
    }
    Instant now = clock.instant();
    AutopilotAction action = AutopilotAction.of(decision.action());
    if (action.movesValve()) {
      CycleContext context =
          new CycleContext(
              levels.currentLevelPct(),
              current.reserveFloorPct(),
              null,
              now,
              current.operatorAbsenceMinutes());
      if (!new ReserveFloorGuard().allows(action, context)) {
        decisions.transition(
            decision.id(),
            DecisionStatus.PENDING,
            DecisionStatus.SKIPPED,
            null,
            ReserveFloorGuard.CODE,
            now);
        return new Result(null, true);
      }
      factory.create(command.caller().aqueductId(), decision, command.caller().userId());
    }
    if (!decisions.transition(
        decision.id(),
        DecisionStatus.PENDING,
        DecisionStatus.ACCEPTED,
        command.caller().userId(),
        null,
        now)) {
      throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION);
    }
    events.publish(
        new OperatorActionRecorded(command.caller().aqueductId(), command.caller().userId(), now));
    return new Result(decisions.find(decision.id()).orElseThrow(), false);
  }

  @Override
  public Class<AcceptDecisionCommand> commandType() {
    return AcceptDecisionCommand.class;
  }

  private record Result(DecisionView decision, boolean blocked) {}
}
