package co.caudal.application.autopilot;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.Map;
import java.util.UUID;

/**
 * The autopilot executes a pending decision in mode {@code AUTOMATIC}: it creates the valve command
 * and marks the decision {@code EXECUTED}.
 *
 * @param caller the autopilot of the aqueduct ({@link Caller#autopilot})
 * @param decisionId the decision
 */
public record ExecuteDecisionCommand(Caller caller, UUID decisionId)
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
    return "AUTOPILOT_EXECUTED";
  }

  @Override
  public Map<String, Object> auditAfterState(DecisionView result) {
    return Map.of(
        "action", result.action(),
        "valve_id", String.valueOf(result.valveId()),
        "valve_command_id", String.valueOf(result.valveCommandId()),
        "source", result.source());
  }
}
