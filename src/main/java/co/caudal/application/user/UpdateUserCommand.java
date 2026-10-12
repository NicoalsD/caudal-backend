package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * Changes the name or the status of an account (docs/API.md, section 3.3; HU-04).
 *
 * @param caller the administrator
 * @param userId the account to change
 * @param fullName the new full name, or null to keep it
 * @param status the new status, or null to keep it
 */
public record UpdateUserCommand(Caller caller, UUID userId, String fullName, String status)
    implements AuditableCommand<ManagedUser> {

  @Override
  public String auditEntityType() {
    return "user";
  }

  @Override
  public String auditEntityId(ManagedUser result) {
    return userId.toString();
  }
}
