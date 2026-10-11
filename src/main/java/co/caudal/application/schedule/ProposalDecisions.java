package co.caudal.application.schedule;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The steps every decision of the Board shares: read the proposal, check that nobody changed it
 * since the person saw it ({@code lock_version}), move it and keep the decision.
 */
public class ProposalDecisions {

  private final ProposalPort proposals;
  private final AqueductPort aqueducts;
  private final Clock clock;

  /**
   * Creates the helper.
   *
   * @param proposals proposals
   * @param aqueducts time zone
   * @param clock source of time
   */
  public ProposalDecisions(ProposalPort proposals, AqueductPort aqueducts, Clock clock) {
    this.proposals = proposals;
    this.aqueducts = aqueducts;
    this.clock = clock;
  }

  /**
   * The proposal as the person saw it.
   *
   * @param caller who decides
   * @param id the proposal
   * @param lockVersion the version the person saw
   * @return the proposal
   * @throws DomainException NOT_FOUND if it does not exist, CONFLICT if it changed
   */
  ProposalDetail current(Caller caller, UUID id, int lockVersion) {
    ProposalDetail detail =
        proposals
            .find(id, today(caller))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (detail.summary().lockVersion() != lockVersion) {
      throw new DomainException(ErrorCode.CONFLICT);
    }
    return detail;
  }

  /**
   * Moves the proposal and keeps the decision.
   *
   * @param caller who decides
   * @param id the proposal
   * @param lockVersion the version the person saw
   * @param status the new status, also the decision
   * @param reason the reason, or null to approve
   * @return the proposal after the decision
   * @throws DomainException CONFLICT if someone changed it meanwhile
   */
  ProposalDetail decide(Caller caller, UUID id, int lockVersion, String status, String reason) {
    if (!proposals.transition(id, lockVersion, status)) {
      throw new DomainException(ErrorCode.CONFLICT);
    }
    proposals.insertDecision(caller.aqueductId(), id, status, reason, caller.userId());
    return proposals.find(id, today(caller)).orElseThrow();
  }

  private LocalDate today(Caller caller) {
    ZoneId zone =
        aqueducts
            .findById(caller.aqueductId())
            .map(AqueductRef::timezone)
            .map(ZoneId::of)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    return LocalDate.ofInstant(clock.instant(), zone);
  }
}
