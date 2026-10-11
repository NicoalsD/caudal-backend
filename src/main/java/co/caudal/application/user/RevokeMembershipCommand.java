package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import co.caudal.application.command.Done;
import java.util.UUID;

/**
 * Ends a membership of the administrator's aqueduct (docs/API.md, section 3.7; HU-04).
 *
 * @param caller the administrator
 * @param membershipId the membership
 */
public record RevokeMembershipCommand(Caller caller, UUID membershipId)
    implements AuditableCommand<Done> {

  @Override
  public String auditEntityType() {
    return "membership";
  }

  @Override
  public String auditEntityId(Done result) {
    return membershipId.toString();
  }
}
