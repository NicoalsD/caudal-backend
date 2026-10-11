package co.caudal.application.mfa;

import java.time.Instant;
import java.util.UUID;

/**
 * The TOTP factor of an account ({@code iam.mfa_factors}).
 *
 * @param id the factor
 * @param userId the account
 * @param secret the encrypted secret
 * @param confirmedAt when the person proved the app works, null until then
 * @param lastUsedStep step of the last accepted code, null if none
 */
public record MfaFactor(
    UUID id, UUID userId, SealedSecret secret, Instant confirmedAt, Long lastUsedStep) {

  /**
   * Tells whether the factor protects the login.
   *
   * @return true once confirmed
   */
  public boolean confirmed() {
    return confirmedAt != null;
  }
}
