package co.caudal.application.publication;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.network.SectorView;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.DocumentRenderingPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.PublicationPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.schedule.ProposalDetail;
import co.caudal.domain.event.SchedulePublished;
import co.caudal.domain.schedule.ProposalState;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.hash.Sha256;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Publishes an approved proposal: it moves it to {@code PUBLISHED} if nobody changed it since the
 * member saw it, and keeps an append-only publication with the message to share and the hash of the
 * poster. The message has only sectors and hours (docs/Reglas-de-negocio.md, section 13). Then it
 * announces {@link SchedulePublished}.
 *
 * @pattern P13 Command
 */
public class PublishScheduleCommandHandler
    implements CommandHandler<PublishScheduleCommand, PublicationView> {

  private final ProposalPort proposals;
  private final PublicationPort publications;
  private final NetworkPort network;
  private final AqueductPort aqueducts;
  private final DocumentRenderingPort documents;
  private final UnitOfWorkPort unitOfWork;
  private final DomainEventPublisher events;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param proposals proposals
   * @param publications publications
   * @param network sector names
   * @param aqueducts name and time zone
   * @param documents renders the poster
   * @param unitOfWork transaction boundary
   * @param events publishes {@code SchedulePublished}
   * @param clock source of time
   */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public PublishScheduleCommandHandler(
      ProposalPort proposals,
      PublicationPort publications,
      NetworkPort network,
      AqueductPort aqueducts,
      DocumentRenderingPort documents,
      UnitOfWorkPort unitOfWork,
      DomainEventPublisher events,
      Clock clock) {
    this.proposals = proposals;
    this.publications = publications;
    this.network = network;
    this.aqueducts = aqueducts;
    this.documents = documents;
    this.unitOfWork = unitOfWork;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public PublicationView handle(PublishScheduleCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          AqueductRef aqueduct =
              aqueducts
                  .findById(command.caller().aqueductId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          ZoneId zone = ZoneId.of(aqueduct.timezone());
          ProposalDetail current =
              proposals
                  .find(command.proposalId(), LocalDate.ofInstant(clock.instant(), zone))
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (current.summary().lockVersion() != command.lockVersion()) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          ProposalState next = ProposalState.of(current.summary().status()).publish();
          List<ScheduleLine> lines = lines(current);
          LocalDate day = current.summary().serviceDate();
          String text =
              new ScheduleNotice(new WhatsAppChannel(), aqueduct.name(), day, zone, lines)
                  .publish();
          String posterSha =
              Sha256.hex(documents.poster(SchedulePoster.of(aqueduct.name(), day, zone, lines)));
          if (!proposals.transition(command.proposalId(), command.lockVersion(), next.code())) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          PublicationView publication =
              publications.insert(
                  aqueduct.id(), command.proposalId(), command.caller().userId(), text, posterSha);
          events.publish(
              new SchedulePublished(
                  aqueduct.id(),
                  command.proposalId(),
                  publication.id(),
                  current.summary().serviceDate(),
                  command.caller().userId(),
                  publication.publishedAt()));
          return publication;
        });
  }

  /** The shifts with the names of their sectors, in their order. */
  List<ScheduleLine> lines(ProposalDetail proposal) {
    Map<UUID, String> names =
        network.sectors().stream().collect(Collectors.toMap(SectorView::id, SectorView::name));
    return proposal.items().stream()
        .map(i -> new ScheduleLine(names.get(i.sectorId()), i.startAt(), i.endAt()))
        .toList();
  }

  @Override
  public Class<PublishScheduleCommand> commandType() {
    return PublishScheduleCommand.class;
  }
}
