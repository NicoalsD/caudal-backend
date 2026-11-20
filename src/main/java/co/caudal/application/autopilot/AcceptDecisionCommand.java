package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.Map;
import java.util.UUID;

/**
 * A person accepts a pending decision: for an OPEN or a CLOSE, this creates the valve command.
 *
 * @param caller who accepts it; needs the permission {@code VALVE_COMMAND_MANUAL}
 * @param decisionId the decision
 */
public record AcceptDecisionCommand(Caller caller, UUID decisionId)
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
    return "AUTOPILOT_DECISION_ACCEPTED";
  }

  @Override
  public Map<String, Object> auditAfterState(DecisionView result) {
    return Map.of(
        "action", result.action(),
        "valve_id", String.valueOf(result.valveId()),
        "valve_command_id", String.valueOf(result.valveCommandId()));
  }
}
