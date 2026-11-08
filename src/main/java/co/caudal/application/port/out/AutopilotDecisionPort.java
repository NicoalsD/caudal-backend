package co.caudal.application.port.out;

import co.caudal.application.autopilot.DecisionView;
import co.caudal.application.autopilot.NewDecision;
import co.caudal.domain.autopilot.DecisionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code ops.autopilot_decisions}. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the table has row level security.
 */
public interface AutopilotDecisionPort {

  /**
   * Stores a decision.
   *
   * @param decision the decision
   * @return the stored decision
   */
  DecisionView insert(NewDecision decision);

  /**
   * Moves every pending decision of the aqueduct of the session to {@code EXPIRED}.
   *
   * @return how many were expired
   */
  int expirePending();

  /**
   * Lists decisions, the newest first.
   *
   * @param status only this status, or null for all
   * @param limit the most to return
   * @return the decisions
   */
  List<DecisionView> list(DecisionStatus status, int limit);

  /**
   * Finds a decision of the aqueduct of the session.
   *
   * @param id the decision
   * @return the decision, or empty
   */
  Optional<DecisionView> find(UUID id);

  /**
   * Moves a decision to another status only if it still has the expected one (compare and set).
   *
   * @param id the decision
   * @param from the status it must have
   * @param to the new status
   * @param decidedBy the person who resolved it, or null
   * @param detail why, or null
   * @param at the moment, stored as {@code decided_at} for ACCEPTED, REJECTED and EXECUTED
   * @return true if the status changed
   */
  boolean transition(
      UUID id, DecisionStatus from, DecisionStatus to, UUID decidedBy, String detail, Instant at);

  /**
   * The last moment a person acted on the valves: a manual command, or accepting or rejecting a
   * decision.
   *
   * @return the moment, or empty if nobody has
   */
  Optional<Instant> lastHumanActionAt();

  /**
   * Marks as executed the accepted decision that originated a command, once the device confirms it.
   *
   * @param commandId the command
   */
  void markExecutedByCommand(UUID commandId);
}
