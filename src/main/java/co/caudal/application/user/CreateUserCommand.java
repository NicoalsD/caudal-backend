package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates an account with a membership in an aqueduct.
 *
 * @param caller the administrator who creates it
 * @param username the username, as typed
 * @param fullName the full name, as typed
 * @param aqueductId the aqueduct of the first membership
 * @param role the role code of the first membership
 */
public record CreateUserCommand(
    Caller caller, String username, String fullName, UUID aqueductId, String role)
    implements AuditableCommand<CreatedUser> {

  /** Validates the parts. */
  public CreateUserCommand {
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(fullName, "fullName");
    Objects.requireNonNull(aqueductId, "aqueductId");
    Objects.requireNonNull(role, "role");
  }

  @Override
  public String auditEntityType() {
    return "user";
  }

  @Override
  public String auditEntityId(CreatedUser result) {
    return result.id().toString();
  }
}
