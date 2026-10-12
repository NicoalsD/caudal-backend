package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.UUID;

/**
 * The Board gives a person a one-time code to set a new password (docs/API.md, section 3.4; HU-02).
 *
 * @param caller the administrator
 * @param userId the account
 * @param reason why, 10 to 500 characters
 */
public record IssueResetGrantCommand(Caller caller, UUID userId, String reason)
    implements AuditableCommand<IssuedResetGrant> {

  @Override
  public String auditEntityType() {
    return "password_reset_grant";
  }

  @Override
  public String auditEntityId(IssuedResetGrant result) {
    return result.id().toString();
  }
}
