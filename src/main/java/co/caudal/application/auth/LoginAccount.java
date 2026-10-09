package co.caudal.application.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Account data needed to check a login.
 *
 * @param id account id
 * @param username username
 * @param fullName full name
 * @param passwordHash stored hash; never logged or returned
 * @param status {@code ACTIVE}, {@code LOCKED} or {@code DISABLED}
 * @param lockedUntil end of the current lock, null if none
 * @param lockoutLevel how many locks the account has had
 * @param tokenVersion current token version
 * @param mustChangePassword true until the first password change
 */
public record LoginAccount(
    UUID id,
    String username,
    String fullName,
    String passwordHash,
    String status,
    Instant lockedUntil,
    int lockoutLevel,
    int tokenVersion,
    boolean mustChangePassword) {

  /** Status of an account that is allowed to sign in. */
  public static final String ACTIVE = "ACTIVE";

  /** Status of a locked account. */
  public static final String LOCKED = "LOCKED";

  /** Status of a deactivated account. */
  public static final String DISABLED = "DISABLED";

  @Override
  public String toString() {
    return "LoginAccount[id=" + id + ", status=" + status + "]";
  }
}
