package co.caudal.application.auth;

import co.caudal.application.command.Command;
import co.caudal.application.command.Done;
import java.util.Objects;

/**
 * Changes the password of the person who is signed in.
 *
 * @param caller who changes it
 * @param currentPassword the password in force, as typed
 * @param newPassword the new password, as typed
 */
public record ChangePasswordCommand(Caller caller, String currentPassword, String newPassword)
    implements Command<Done> {

  /** Validates the parts. */
  public ChangePasswordCommand {
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(currentPassword, "currentPassword");
    Objects.requireNonNull(newPassword, "newPassword");
  }

  @Override
  public String toString() {
    return "ChangePasswordCommand[REDACTED]";
  }
}
