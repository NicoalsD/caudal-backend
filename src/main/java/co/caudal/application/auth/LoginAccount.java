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
 * @param aqueductId institution of the account, null only for the platform superadmin
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record LoginAccount(
    UUID id,
    String username,
    String fullName,
    String passwordHash,
    String status,
    Instant lockedUntil,
    int lockoutLevel,
    int tokenVersion,
    boolean mustChangePassword,
    UUID aqueductId) {

  /**
   * Account without a known institution (used when the institution is not part of the lookup).
   *
   * @param id account id
   * @param username username
   * @param fullName full name
   * @param passwordHash stored hash
   * @param status account status
   * @param lockedUntil end of the current lock, null if none
   * @param lockoutLevel how many locks the account has had
   * @param tokenVersion current token version
   * @param mustChangePassword true until the first password change
   */
  public LoginAccount(
      UUID id,
      String username,
      String fullName,
      String passwordHash,
      String status,
      Instant lockedUntil,
      int lockoutLevel,
      int tokenVersion,
      boolean mustChangePassword) {
    this(
        id,
        username,
        fullName,
        passwordHash,
        status,
        lockedUntil,
        lockoutLevel,
        tokenVersion,
        mustChangePassword,
        null);
  }

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
