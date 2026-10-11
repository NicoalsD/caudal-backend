package co.caudal.application.schedule;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board approves a proposal as it is (docs/API.md, section 10.4; HU-20).
 *
 * @param caller a member of the Board
 * @param proposalId the proposal
 * @param lockVersion the version the member saw
 */
public record ApproveProposalCommand(Caller caller, UUID proposalId, int lockVersion)
    implements AuditableCommand<ProposalDetail> {

  @Override
  public String auditEntityType() {
    return "schedule_proposal";
  }

  @Override
  public String auditEntityId(ProposalDetail result) {
    return proposalId.toString();
  }
}
