package co.caudal.application.schedule;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.domain.schedule.ProposalState;

/**
 * Rejects a proposal pending review with its reason. It is a final status: the day gets a new
 * proposal.
 *
 * @pattern P13 Command
 */
public class RejectProposalCommandHandler
    implements CommandHandler<RejectProposalCommand, ProposalDetail> {

  private final ProposalDecisions decisions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param decisions shared steps of a decision
   * @param unitOfWork transaction boundary
   */
  public RejectProposalCommandHandler(ProposalDecisions decisions, UnitOfWorkPort unitOfWork) {
    this.decisions = decisions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ProposalDetail handle(RejectProposalCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ProposalDetail current =
              decisions.current(command.caller(), command.proposalId(), command.lockVersion());
          ProposalState next = ProposalState.of(current.summary().status()).reject();
          ChangeReason reason = ChangeReason.require(command.reason());
          return decisions.decide(
              command.caller(),
              command.proposalId(),
              command.lockVersion(),
              next.code(),
              reason.value());
        });
  }

  @Override
  public Class<RejectProposalCommand> commandType() {
    return RejectProposalCommand.class;
  }
}
