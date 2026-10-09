package co.caudal.application.auth;

import java.util.UUID;

/**
 * One row of {@code iam.login_attempts}. Neither the IP nor the typed username is stored, only
 * their keyed hashes.
 *
 * @param userId account if it exists
 * @param usernameHmac keyed hash of the typed username
 * @param ipHmac keyed hash of the IP
 * @param succeeded result
 * @param failureReason reason of the failure, null on success
 */
public record LoginAttempt(
    UUID userId,
    String usernameHmac,
    String ipHmac,
    boolean succeeded,
    FailureReason failureReason) {

  /** Reasons accepted by the {@code failure_reason} column. */
  public enum FailureReason {
    INVALID_CREDENTIALS,
    LOCKED,
    DISABLED,
    RATE_LIMITED,
    MFA_FAILED
  }
}
