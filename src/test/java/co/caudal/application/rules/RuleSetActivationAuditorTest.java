package co.caudal.application.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.caudal.application.command.AuditEntry;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.domain.event.RuleSetActivated;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuleSetActivationAuditorTest {

  private final AuditLogPort auditLog = mock(AuditLogPort.class);
  private final RuleSetActivationAuditor auditor = new RuleSetActivationAuditor(auditLog);

  @Test
  void writesTheActivationWithItsReason() {
    UUID aqueduct = UUID.randomUUID();
    UUID ruleSet = UUID.randomUUID();
    UUID previous = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-11T15:00:00Z");

    auditor.on(new RuleSetActivated(aqueduct, ruleSet, 4, previous, admin, "Motivo largo.", at));
    auditor.on(new RuleSetActivated(aqueduct, ruleSet, 1, null, admin, "Primera versión.", at));

    ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditLog, org.mockito.Mockito.times(2)).append(entry.capture());
    AuditEntry first = entry.getAllValues().getFirst();
    assertThat(first.action()).isEqualTo("RULESET_ACTIVATED");
    assertThat(first.entityId()).isEqualTo(ruleSet.toString());
    assertThat(first.actorId()).isEqualTo(admin);
    assertThat(first.afterState())
        .containsEntry("version", 4)
        .containsEntry("change_reason", "Motivo largo.")
        .containsEntry("superseded_rule_set_id", previous.toString());
    assertThat(entry.getAllValues().get(1).afterState())
        .doesNotContainKey("superseded_rule_set_id");
    assertThat(auditor.eventType()).isEqualTo(RuleSetActivated.class);
  }
}
