package co.caudal.application.port.out;

import co.caudal.application.schedule.ProposalDetail;
import co.caudal.domain.schedule.ScheduleProposal;
import java.time.LocalDate;
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
}
