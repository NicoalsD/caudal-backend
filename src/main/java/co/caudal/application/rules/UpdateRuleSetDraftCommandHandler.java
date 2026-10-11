package co.caudal.application.rules;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.rules.RuleSet;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * Applies the Board's changes to a draft (docs/Reglas-de-negocio.md, section 1.3). Only a draft can
 * change: an active or past version answers {@code RULE_SET_IMMUTABLE}, and the database trigger
 * refuses it too. Whether the bands cover the gauge is checked when the draft is activated.
 *
 * @pattern P13 Command
 */
public class UpdateRuleSetDraftCommandHandler
    implements CommandHandler<UpdateRuleSetDraftCommand, RuleSetDetail> {

  private final RuleSetPort ruleSets;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param ruleSets versions of the rules
   * @param unitOfWork transaction boundary
   */
  public UpdateRuleSetDraftCommandHandler(RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public RuleSetDetail handle(UpdateRuleSetDraftCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> update(command));
  }

  private RuleSetDetail update(UpdateRuleSetDraftCommand command) {
    RuleSetDetail current =
        ruleSets
            .findDetail(command.ruleSetId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSet draft = RuleSetConversion.toDomain(current);
    if (!draft.isDraft()) {
      throw new DomainException(ErrorCode.RULE_SET_IMMUTABLE);
    }
    RuleSet changed = command.changes().applyTo(draft);
    String reason =
        command.changes().changeReason() == null
            ? current.changeReason()
            : command.changes().changeReason();
    ruleSets.updateDraft(command.ruleSetId(), changed.parameters(), reason);
    if (command.changes().replacesChildren()) {
      ruleSets.replaceChildren(command.ruleSetId(), changed);
    }
    return ruleSets.findDetail(command.ruleSetId()).orElseThrow();
  }

  @Override
  public Class<UpdateRuleSetDraftCommand> commandType() {
    return UpdateRuleSetDraftCommand.class;
  }
}
