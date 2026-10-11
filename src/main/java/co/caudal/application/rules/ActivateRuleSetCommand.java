package co.caudal.application.rules;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
import java.util.UUID;

/**
 * Puts a draft of the rules in force (docs/Reglas-de-negocio.md, section 1.2; HU-08). Its audit
 * line is written by the subscriber of {@code RuleSetActivated}, with the reason in {@code
 * after_state}.
 *
 * @param caller the Board administrator
 * @param ruleSetId the draft
 * @param changeReason why it is activated
 */
public record ActivateRuleSetCommand(Caller caller, UUID ruleSetId, String changeReason)
    implements Command<RuleSetDetail> {}
