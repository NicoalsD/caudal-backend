package co.caudal.application.mfa;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.Command;

/**
 * Proves that the authenticator app works, turns the factor on and issues the recovery codes
 * (HU-06).
 *
 * @param caller the person
 * @param code the code shown by the app
 */
public record ConfirmMfaCommand(Caller caller, String code) implements Command<MfaConfirmation> {

  @Override
  public String toString() {
    return "ConfirmMfaCommand[REDACTED]";
  }
}
