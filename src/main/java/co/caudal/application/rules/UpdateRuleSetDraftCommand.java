package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Edits the draft of a new version of the rules (docs/API.md, section 6.5; HU-07).
 *
 * @param caller a member of the Board
 * @param ruleSetId the draft
 * @param changes what changes
 */
public record UpdateRuleSetDraftCommand(Caller caller, UUID ruleSetId, RuleSetChanges changes)
    implements AuditableCommand<RuleSetDetail> {

  @Override
  public String auditEntityType() {
    return "rule_set";
  }

  @Override
  public String auditEntityId(RuleSetDetail result) {
    return ruleSetId.toString();
  }
}
