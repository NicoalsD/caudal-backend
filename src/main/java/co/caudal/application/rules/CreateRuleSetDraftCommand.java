package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;

/**
 * Starts a new version of the rules as a copy of the active one (docs/API.md, section 6.4; HU-07).
 *
 * @param caller a member of the Board
 * @param changeReason why the rules change, or null for now (it is required to activate)
 */
public record CreateRuleSetDraftCommand(Caller caller, String changeReason)
    implements AuditableCommand<RuleSetDetail> {

  @Override
  public String auditEntityType() {
    return "rule_set";
  }

  @Override
  public String auditEntityId(RuleSetDetail result) {
    return result.rules().id().toString();
  }
}
