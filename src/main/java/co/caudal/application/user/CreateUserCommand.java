package co.caudal.application.user;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
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
    implements Command<CreatedUser> {

  /** Validates the parts. */
  public CreateUserCommand {
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(fullName, "fullName");
    Objects.requireNonNull(aqueductId, "aqueductId");
    Objects.requireNonNull(role, "role");
  }
}
