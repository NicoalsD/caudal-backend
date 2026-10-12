package co.caudal.infrastructure.publication;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.publication.PublicSchedule;
import co.caudal.application.publication.ScheduleLine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicScheduleProxyTest {

  static final LocalDate DAY = LocalDate.of(2026, 10, 12);
  static final Instant AT = Instant.parse("2026-10-12T10:00:00Z");

  static PublicSchedule stored(boolean enabled, String status, UUID publication) {
    return new PublicSchedule(
        "Acueducto Demo",
        true,
        enabled,
        DAY,
        status,
        publication,
        publication == null ? null : AT,
        UUID.randomUUID(),
        "Horario",
        null,
        List.of(new ScheduleLine("Sector Alto", AT, AT.plusSeconds(3600))));
  }

  static Optional<PublicSchedule> through(PublicSchedule stored) {
    return new PublicScheduleProxy(day -> Optional.of(stored)).find(DAY);
  }

  @Test
  void aPublishedScheduleLosesWhoPublishedItAndItsInternalStatus() {
    UUID publication = UUID.randomUUID();

    PublicSchedule shown = through(stored(true, "PUBLISHED", publication)).orElseThrow();

    assertThat(shown.publishedBy()).isNull();
    assertThat(shown.proposalStatus()).isNull();
    assertThat(shown.publicationId()).isEqualTo(publication);
    assertThat(shown.lines()).extracting(ScheduleLine::sectorName).containsExactly("Sector Alto");
    assertThat(through(stored(true, "CLOSED", publication))).isPresent();
  }

  @Test
  void anUnpublishedScheduleOrAClosedPageShowsNothing() {
    assertThat(through(stored(true, "APPROVED", null))).isEmpty();
    assertThat(through(stored(true, "PENDING_REVIEW", null))).isEmpty();
    assertThat(through(stored(false, "PUBLISHED", UUID.randomUUID()))).isEmpty();
    assertThat(new PublicScheduleProxy(day -> Optional.empty()).find(DAY)).isEmpty();
  }
}
