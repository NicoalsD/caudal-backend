package co.caudal.application.schedule;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.publication.PublicationView;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Reads the published schedule of a day for the shift executions (docs/API.md, section 11.4). Only
 * the proposal in status PUBLISHED or CLOSED counts: a closed day still shows the shifts it had,
 * and drafts and proposals pending review are not visible.
 */
public class GetDayScheduleUseCase {

  /** Statuses whose schedule the families already saw: published, and published then closed. */
  private static final List<String> VISIBLE_STATUSES = List.of("PUBLISHED", "CLOSED");

  private final ProposalPort proposals;
  private final PublicationPort publications;
  private final AqueductPort aqueducts;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param proposals proposals
   * @param publications publications
   * @param aqueducts time zone
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GetDayScheduleUseCase(
      ProposalPort proposals,
      PublicationPort publications,
      AqueductPort aqueducts,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.proposals = proposals;
    this.publications = publications;
    this.aqueducts = aqueducts;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Reads the published schedule of a day.
   *
   * @param caller who asks
   * @param serviceDate the service day in the time zone of the aqueduct
   * @return the published proposal and its publication time
   * @throws DomainException NOT_FOUND if the day has no published schedule in the aqueduct
   */
  public PublishedDay execute(Caller caller, LocalDate serviceDate) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          AqueductRef aqueduct =
              aqueducts
                  .findById(caller.aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          ZoneId zone = ZoneId.of(aqueduct.timezone());
          UUID proposalId = firstPublished(serviceDate);
          ProposalDetail detail =
              proposals
                  .find(proposalId, LocalDate.ofInstant(clock.instant(), zone))
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          PublicationView publication =
              publications
                  .findByProposal(proposalId)
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return new PublishedDay(detail, publication.publishedAt());
        });
  }

  private UUID firstPublished(LocalDate serviceDate) {
    return VISIBLE_STATUSES.stream()
        .flatMap(status -> proposals.list(serviceDate, serviceDate, status, null, 1).stream())
        .findFirst()
        .map(ProposalSummary::id)
        .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
  }
}
