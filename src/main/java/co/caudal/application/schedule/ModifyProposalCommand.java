package co.caudal.application.schedule;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.List;
import java.util.UUID;

/**
 * The Board changes the shifts of a proposal and approves it (docs/API.md, section 10.5; HU-21).
 *
 * @param caller a member of the Board
 * @param proposalId the proposal
 * @param lockVersion the version the member saw
 * @param reason why it changes
 * @param items every shift the proposal keeps
 */
public record ModifyProposalCommand(
    Caller caller, UUID proposalId, int lockVersion, String reason, List<ModifiedItem> items)
    implements AuditableCommand<ProposalDetail> {

  /** Copies the shifts. */
  public ModifyProposalCommand {
    items = List.copyOf(items);
  }

  @Override
  public String auditEntityType() {
    return "schedule_proposal";
  }

  @Override
  public String auditEntityId(ProposalDetail result) {
    return proposalId.toString();
  }
}
