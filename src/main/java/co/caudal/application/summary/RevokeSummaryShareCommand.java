package co.caudal.application.summary;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board revokes an authorization at any time (docs/API.md, section 13.5). Nothing is deleted:
 * the authorization keeps its row with {@code revoked_at} and {@code revoked_by}.
 *
 * @param caller the member of the Board who revokes it
 * @param shareId the authorization
 */
public record RevokeSummaryShareCommand(Caller caller, UUID shareId)
    implements AuditableCommand<SummaryShareView> {

  @Override
  public String auditEntityType() {
    return "summary_share";
  }

  @Override
  public String auditEntityId(SummaryShareView result) {
    return shareId.toString();
  }
}
