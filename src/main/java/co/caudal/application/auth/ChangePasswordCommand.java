package co.caudal.application.auth;

import co.caudal.application.command.AuditableCommand;
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
    implements AuditableCommand<Done> {

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

  @Override
  public String auditEntityType() {
    return "user";
  }

  @Override
  public String auditEntityId(Done result) {
    return caller.userId().toString();
  }
}
