package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.Instant;
import java.util.UUID;

/**
 * Grants a role in an aqueduct to an existing account (docs/API.md, section 3.6; HU-04).
 *
 * @param caller the administrator
 * @param userId the account
 * @param aqueductId the aqueduct; it must be the one of the session
 * @param roleCode the role
 * @param validFrom start of the validity, or null for now
 * @param validTo end of the validity, or null while open
 */
public record GrantMembershipCommand(
    Caller caller,
    UUID userId,
    UUID aqueductId,
    String roleCode,
    Instant validFrom,
    Instant validTo)
    implements AuditableCommand<MembershipView> {

  @Override
  public String auditEntityType() {
    return "membership";
  }

  @Override
  public String auditEntityId(MembershipView result) {
    return result.id().toString();
  }
}
