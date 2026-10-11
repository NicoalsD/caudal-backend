package co.caudal.application.publication;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board publishes an approved schedule (docs/API.md, section 10.7; HU-23).
 *
 * @param caller a member of the Board
 * @param proposalId the approved proposal
 * @param lockVersion the version the member saw
 */
public record PublishScheduleCommand(Caller caller, UUID proposalId, int lockVersion)
    implements AuditableCommand<PublicationView> {

  @Override
  public String auditEntityType() {
    return "publication";
  }

  @Override
  public String auditEntityId(PublicationView result) {
    return result.id().toString();
  }
}
