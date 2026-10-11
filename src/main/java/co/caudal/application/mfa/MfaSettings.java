package co.caudal.application.mfa;

import java.util.Set;

/**
 * Settings of the second factor.
 *
 * @param issuer name shown by the authenticator app
 * @param allowedRoles roles that may turn MFA on (docs/Seguridad.md, section 2.4)
 */
public record MfaSettings(String issuer, Set<String> allowedRoles) {

  /** Copies the set so the settings cannot change. */
  public MfaSettings {
    allowedRoles = Set.copyOf(allowedRoles);
  }
}
