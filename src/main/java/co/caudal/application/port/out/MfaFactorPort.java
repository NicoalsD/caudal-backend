package co.caudal.application.port.out;

import co.caudal.application.mfa.MfaFactor;
import co.caudal.application.mfa.SealedSecret;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores the TOTP factor of each account ({@code iam.mfa_factors}). */
public interface MfaFactorPort {

  /**
   * Finds the factor of an account.
   *
   * @param userId the account
   * @return the factor, confirmed or not, or empty
   */
  Optional<MfaFactor> find(UUID userId);

  /**
   * Stores a new, unconfirmed factor.
   *
   * @param userId the account
   * @param secret the encrypted secret
   */
  void insert(UUID userId, SealedSecret secret);

  /**
   * Deletes the factor of an account if it was never confirmed.
   *
   * @param userId the account
   */
  void deleteUnconfirmed(UUID userId);

  /**
   * Marks the factor as confirmed with the step of the code that proved it.
   *
   * @param factorId the factor
   * @param at moment of the confirmation
   * @param step step of the accepted code
   */
  void confirm(UUID factorId, Instant at, long step);

  /**
   * Records the step of an accepted code, so it cannot be used again. Two requests with the same
   * code race on this update and only one of them wins.
   *
   * @param factorId the factor
   * @param step step of the accepted code
   * @return true if the step was recorded, false if the same or a later step was already used
   */
  boolean markUsed(UUID factorId, long step);
}
