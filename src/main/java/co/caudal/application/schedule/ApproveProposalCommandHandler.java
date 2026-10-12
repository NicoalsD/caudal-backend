package co.caudal.application.schedule;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.schedule.ProposalState;

/**
 * Approves a proposal pending review. The {@code lock_version} the member saw must still be the
 * current one, so two members deciding at once never overwrite each other.
 *
 * @pattern P13 Command
 */
public class ApproveProposalCommandHandler
    implements CommandHandler<ApproveProposalCommand, ProposalDetail> {

  private final ProposalDecisions decisions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param decisions shared steps of a decision
   * @param unitOfWork transaction boundary
   */
  public ApproveProposalCommandHandler(ProposalDecisions decisions, UnitOfWorkPort unitOfWork) {
    this.decisions = decisions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ProposalDetail handle(ApproveProposalCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ProposalDetail current =
              decisions.current(command.caller(), command.proposalId(), command.lockVersion());
          ProposalState next = ProposalState.of(current.summary().status()).approve();
          return decisions.decide(
              command.caller(), command.proposalId(), command.lockVersion(), next.code(), null);
        });
  }

  @Override
  public Class<ApproveProposalCommand> commandType() {
    return ApproveProposalCommand.class;
  }
}
