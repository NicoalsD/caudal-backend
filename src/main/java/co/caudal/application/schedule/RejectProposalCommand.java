package co.caudal.application.schedule;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board rejects a proposal (docs/API.md, section 10.6; HU-22).
 *
 * @param caller a member of the Board
 * @param proposalId the proposal
 * @param lockVersion the version the member saw
 * @param reason why
 */
public record RejectProposalCommand(Caller caller, UUID proposalId, int lockVersion, String reason)
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
