package co.caudal.application.rules;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.rules.RuleSet;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the draft of a new version by cloning the active one with {@link
 * RuleSet#copyForNewVersion(int)} (docs/Reglas-de-negocio.md, section 1.1). An aqueduct has at most
 * one draft at a time.
 *
 * @pattern P13 Command
 */
public class CreateRuleSetDraftCommandHandler
    implements CommandHandler<CreateRuleSetDraftCommand, RuleSetDetail> {

  private final RuleSetPort ruleSets;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param ruleSets versions of the rules
   * @param unitOfWork transaction boundary
   */
  public CreateRuleSetDraftCommandHandler(RuleSetPort ruleSets, UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public RuleSetDetail handle(CreateRuleSetDraftCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> create(command));
  }

  private RuleSetDetail create(CreateRuleSetDraftCommand command) {
    if (ruleSets.draftExists()) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "DRAFT_EXISTS"));
    }
    RuleSetView active =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSetDetail origin =
        ruleSets
            .findDetail(active.id())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSet draft = RuleSetConversion.toDomain(origin).copyForNewVersion(ruleSets.maxVersion() + 1);
    UUID id =
        ruleSets.insertDraft(
            command.caller().aqueductId(),
            draft,
            command.caller().userId(),
            command.changeReason());
    return ruleSets.findDetail(id).orElseThrow();
  }

  @Override
  public Class<CreateRuleSetDraftCommand> commandType() {
    return CreateRuleSetDraftCommand.class;
  }
}
