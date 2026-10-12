package co.caudal.application.mfa;

import java.util.List;

/**
 * Result of turning MFA on: the recovery codes, shown once.
 *
 * @param recoveryCodes one-time codes for when the authenticator app is lost
 */
public record MfaConfirmation(List<String> recoveryCodes) {

  /** Copies the list so the result cannot change. */
  public MfaConfirmation {
    recoveryCodes = List.copyOf(recoveryCodes);
  }

  @Override
  public String toString() {
    return "MfaConfirmation[REDACTED]";
  }
}
