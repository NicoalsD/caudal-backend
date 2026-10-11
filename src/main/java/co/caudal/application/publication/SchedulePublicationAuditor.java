package co.caudal.application.publication;

import co.caudal.application.command.AuditEntry;
import co.caudal.application.event.DomainEventSubscriber;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.domain.event.SchedulePublished;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the publication of a schedule in the audit log when {@link SchedulePublished} arrives.
 *
 * @pattern P17 Observer
 */
public class SchedulePublicationAuditor implements DomainEventSubscriber<SchedulePublished> {

  static final String ACTION = "SCHEDULE_PUBLISHED";

  private final AuditLogPort auditLog;

  /**
   * Creates the subscriber.
   *
   * @param auditLog audit log
   */
  public SchedulePublicationAuditor(AuditLogPort auditLog) {
    this.auditLog = auditLog;
  }

  @Override
  public Class<SchedulePublished> eventType() {
    return SchedulePublished.class;
  }

  @Override
  public void on(SchedulePublished event) {
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("proposal_id", event.proposalId().toString());
    after.put("service_date", event.serviceDate().toString());
    after.put("published_at", event.occurredAt().toString());
    auditLog.append(
        new AuditEntry(
            event.aqueductId(),
            event.publishedBy(),
            ACTION,
            "publication",
            event.publicationId().toString(),
            null,
            after));
  }
}
