package co.caudal.infrastructure.publication;

import co.caudal.application.port.out.PublicSchedulePort;
import co.caudal.application.publication.PublicSchedule;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Stands in front of the reader of schedules for the public page (docs/Reglas-de-negocio.md,
 * section 13). It shows nothing when the aqueduct turned its page off or the day has no published
 * schedule, and it removes what is internal before answering: who published it and the status of
 * the proposal. The use case does not know it is talking to a proxy.
 *
 * @pattern P11 Proxy
 */
public final class PublicScheduleProxy implements PublicSchedulePort {

  private static final Set<String> PUBLIC_STATUSES = Set.of("PUBLISHED", "CLOSED");

  private final PublicSchedulePort reader;

  /**
   * Creates the proxy.
   *
   * @param reader the real reader
   */
  public PublicScheduleProxy(PublicSchedulePort reader) {
    this.reader = reader;
  }

  @Override
  public Optional<PublicSchedule> find(LocalDate day) {
    return reader
        .find(day)
        .filter(PublicSchedule::publicPageEnabled)
        .filter(s -> s.publicationId() != null && PUBLIC_STATUSES.contains(s.proposalStatus()))
        .map(
            s ->
                new PublicSchedule(
                    s.aqueductName(),
                    s.demo(),
                    true,
                    s.serviceDate(),
                    null,
                    s.publicationId(),
                    s.publishedAt(),
                    null,
                    s.whatsappText(),
                    s.posterSha256(),
                    s.lines()));
  }
}
