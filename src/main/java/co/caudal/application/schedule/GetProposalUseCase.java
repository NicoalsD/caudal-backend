package co.caudal.application.schedule;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reads a full proposal with its shifts, reasons and decisions (docs/API.md, section 10.3). The
 * texts of the reasons say "hoy" or "mañana" from the local day of the aqueduct.
 */
public class GetProposalUseCase {

  private final ProposalPort proposals;
  private final AqueductPort aqueducts;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param proposals proposals
   * @param aqueducts time zone
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GetProposalUseCase(
      ProposalPort proposals, AqueductPort aqueducts, UnitOfWorkPort unitOfWork, Clock clock) {
    this.proposals = proposals;
    this.aqueducts = aqueducts;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads one proposal.
   *
   * @param caller who asks
   * @param id the proposal
   * @return the proposal
   * @throws DomainException NOT_FOUND if it does not exist in the aqueduct
   */
  public ProposalDetail execute(Caller caller, UUID id) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          ZoneId zone =
              aqueducts
                  .findById(caller.aqueductId())
                  .map(AqueductRef::timezone)
                  .map(ZoneId::of)
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return proposals
              .find(id, LocalDate.ofInstant(clock.instant(), zone))
              .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        });
  }
}
