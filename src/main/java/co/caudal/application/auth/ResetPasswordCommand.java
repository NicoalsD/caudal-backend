package co.caudal.application.auth;

import co.caudal.application.command.Command;
import java.util.Objects;

/**
 * Sets a password with a one-time code. There is no signed-in person: the code is the proof.
 *
 * @param username the account, as typed
 * @param code the one-time code, as typed
 * @param newPassword the new password, as typed
 */
public record ResetPasswordCommand(String username, String code, String newPassword)
    implements Command<PasswordResetResult> {

  /** Validates the parts. */
  public ResetPasswordCommand {
    Objects.requireNonNull(username, "username");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(newPassword, "newPassword");
  }

  @Override
  public String toString() {
    return "ResetPasswordCommand[REDACTED]";
  }
}
