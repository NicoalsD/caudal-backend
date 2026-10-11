package co.caudal.application.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.caudal.application.command.AuditEntry;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.domain.event.SchedulePublished;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SchedulePublicationAuditorTest {

  private final AuditLogPort auditLog = mock(AuditLogPort.class);
  private final SchedulePublicationAuditor auditor = new SchedulePublicationAuditor(auditLog);

  @Test
  void writesThePublicationWithItsDay() {
    UUID aqueduct = UUID.randomUUID();
    UUID proposal = UUID.randomUUID();
    UUID publication = UUID.randomUUID();
    UUID member = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-11T15:00:00Z");

    auditor.on(
        new SchedulePublished(
            aqueduct, proposal, publication, LocalDate.of(2026, 10, 12), member, at));

    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLog).append(entry.capture());
    assertThat(entry.getValue().action()).isEqualTo("SCHEDULE_PUBLISHED");
    assertThat(entry.getValue().entityId()).isEqualTo(publication.toString());
    assertThat(entry.getValue().actorId()).isEqualTo(member);
    assertThat(entry.getValue().afterState())
        .containsEntry("proposal_id", proposal.toString())
        .containsEntry("service_date", "2026-10-12");
    assertThat(auditor.eventType()).isEqualTo(SchedulePublished.class);
  }
}
