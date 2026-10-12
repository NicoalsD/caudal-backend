package co.caudal.application.mfa;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;

/**
 * Starts the enrollment of a TOTP factor for the caller's own account (HU-06).
 *
 * @param caller the person
 */
public record EnrollMfaCommand(Caller caller) implements AuditableCommand<MfaEnrollment> {

  @Override
  public String auditEntityType() {
    return "mfa_factor";
  }

  @Override
  public String auditEntityId(MfaEnrollment result) {
    return caller.userId().toString();
  }
}
