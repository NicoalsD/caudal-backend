package co.caudal.application.rules;

import co.caudal.application.command.AuditEntry;
import co.caudal.application.event.DomainEventSubscriber;
import co.caudal.application.port.out.AuditLogPort;
import co.caudal.domain.event.RuleSetActivated;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code RULESET_ACTIVATED} to the audit log with the reason in {@code after_state}
 * (docs/Reglas-de-negocio.md, sections 1.2 and 7).
 *
 * @pattern P17 Observer
 */
public class RuleSetActivationAuditor implements DomainEventSubscriber<RuleSetActivated> {

  /** Action written to the audit log. */
  static final String ACTION = "RULESET_ACTIVATED";

  private final AuditLogPort auditLog;

  /**
   * Creates the subscriber.
   *
   * @param auditLog the audit log
   */
  public RuleSetActivationAuditor(AuditLogPort auditLog) {
    this.auditLog = auditLog;
  }

  @Override
  public Class<RuleSetActivated> eventType() {
    return RuleSetActivated.class;
  }

  @Override
  public void on(RuleSetActivated event) {
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("version", event.version());
    after.put("valid_from", event.occurredAt().toString());
    after.put("change_reason", event.changeReason());
    if (event.supersededRuleSetId() != null) {
      after.put("superseded_rule_set_id", event.supersededRuleSetId().toString());
    }
    auditLog.append(
        new AuditEntry(
            event.aqueductId(),
            event.activatedBy(),
            ACTION,
            "rule_set",
            event.ruleSetId().toString(),
            null,
            after));
  }
}
