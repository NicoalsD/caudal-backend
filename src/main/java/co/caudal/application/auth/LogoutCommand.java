package co.caudal.application.auth;

import co.caudal.application.command.Command;
import co.caudal.application.command.Done;
import java.util.Objects;

/**
 * Closes the session of the refresh token that came in the cookie.
 *
 * @param caller who closes the session
 * @param refreshSecret raw value of the {@code caudal_rt} cookie, null if the request had none
 */
public record LogoutCommand(Caller caller, String refreshSecret) implements Command<Done> {

  /** Validates the caller. */
  public LogoutCommand {
    Objects.requireNonNull(caller, "caller");
  }

  @Override
  public String toString() {
    return "LogoutCommand[REDACTED]";
  }
}
