package co.caudal.application.auth;

import co.caudal.application.command.Command;
import co.caudal.application.command.Done;
import java.util.Objects;

/**
 * Closes every session of the account, on every device.
 *
 * @param caller whose sessions are closed
 */
public record LogoutAllCommand(Caller caller) implements Command<Done> {

  /** Validates the caller. */
  public LogoutAllCommand {
    Objects.requireNonNull(caller, "caller");
  }
}
