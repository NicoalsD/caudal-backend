package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.Map;
import java.util.UUID;

/**
 * A person rejects a pending decision. No command is created.
 *
 * @param caller who rejects it; needs the permission {@code ASSISTANT_USE}
 * @param decisionId the decision
 * @param reason why, or null
 */
public record RejectDecisionCommand(Caller caller, UUID decisionId, String reason)
    implements AuditableCommand<DecisionView> {

  @Override
  public String auditEntityType() {
    return "autopilot_decision";
  }

  @Override
  public String auditEntityId(DecisionView result) {
    return result.id().toString();
  }

  @Override
  public String auditAction() {
    return "AUTOPILOT_DECISION_REJECTED";
  }

  @Override
  public Map<String, Object> auditAfterState(DecisionView result) {
    return Map.of("action", result.action(), "valve_id", String.valueOf(result.valveId()));
  }
}
