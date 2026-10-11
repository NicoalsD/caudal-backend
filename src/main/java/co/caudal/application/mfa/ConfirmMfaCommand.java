package co.caudal.application.mfa;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;
import co.caudal.application.command.Done;

/**
 * Proves that the authenticator app works and turns the factor on (HU-06).
 *
 * @param caller the person
 * @param code the code shown by the app
 */
public record ConfirmMfaCommand(Caller caller, String code) implements Command<Done> {

  @Override
  public String toString() {
    return "ConfirmMfaCommand[REDACTED]";
  }
}
