package co.caudal.application.port.out;

import co.caudal.application.schedule.ProposalDetail;
import co.caudal.application.schedule.ProposalSummary;
import co.caudal.domain.schedule.ProposalMemento;
import co.caudal.domain.schedule.ProposedTurn;
import co.caudal.domain.schedule.ScheduleProposal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores proposals of shifts with their items and reasons. RLS limits the aqueduct. */
public interface ProposalPort {

  /**
   * Tells whether a day already has a proposal that is not rejected.
   *
   * @param serviceDate the day
   * @return true if one is pending, approved, published or closed
   */
  boolean openExists(LocalDate serviceDate);

  /**
   * Stores a new proposal pending review.
   *
   * @param aqueductId the aqueduct
   * @param proposal the proposal
   * @param generatedBy who asked for it
   * @return its id
   */
  UUID insert(UUID aqueductId, ScheduleProposal proposal, UUID generatedBy);

  /**
   * Reads a full proposal.
   *
   * @param id the proposal
   * @param today the day the texts are read, for "hoy" and "mañana"
   * @return the proposal, or empty
   */
  Optional<ProposalDetail> find(UUID id, LocalDate today);

  /**
   * Reads a page of proposals, the newest first.
   *
   * @param from first day of service, or null
   * @param to last day of service, or null
   * @param status only this status, or null
   * @param beforeId only proposals older than this one, or null
   * @param limit maximum rows
   * @return the proposals without their shifts
   */
  List<ProposalSummary> list(LocalDate from, LocalDate to, String status, UUID beforeId, int limit);

  /**
   * Moves a proposal to another status if nobody changed it meanwhile, and bumps its version.
   *
   * @param id the proposal
   * @param lockVersion the version the person saw
   * @param status the new status
   * @return true if it changed, false if the version no longer matches
   */
  boolean transition(UUID id, int lockVersion, String status);

  /**
   * Stores a decision of the Board.
   *
   * @param aqueductId the aqueduct
   * @param proposalId the proposal
   * @param decision APPROVED, APPROVED_WITH_CHANGES or REJECTED
   * @param reason the reason, or null to approve
   * @param decidedBy who decided
   */
  void insertDecision(
      UUID aqueductId, UUID proposalId, String decision, String reason, UUID decidedBy);

  /**
   * Stores the snapshot of a proposal before its first change.
   *
   * @param aqueductId the aqueduct
   * @param memento the snapshot
   */
  void saveSnapshot(UUID aqueductId, ProposalMemento memento);

  /**
   * Replaces every shift of a proposal pending review, with their reasons.
   *
   * @param aqueductId the aqueduct
   * @param proposalId the proposal
   * @param turns the new shifts
   */
  void replaceItems(UUID aqueductId, UUID proposalId, List<ProposedTurn> turns);
}
