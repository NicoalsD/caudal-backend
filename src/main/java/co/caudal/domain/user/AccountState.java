package co.caudal.domain.user;

import java.time.Instant;

/**
 * State of an account (docs/Seguridad.md, section 2.2; docs/API.md, section 3.3). Each state knows
 * whether it lets the person sign in and which changes an administrator may make from it, so the
 * login and the administration of users share one set of rules.
 *
 * <p>A lock caused by failed logins carries its end ({@code lockedUntil}) and stops working by
 * itself when that moment passes; a lock set by an administrator has no end and lasts until an
 * administrator unlocks the account. A deactivated account never comes back.
 *
 * @pattern P18 State
 */
public sealed interface AccountState
    permits ActiveAccountState, LockedAccountState, DisabledAccountState {

  /** Code stored in {@code iam.users.status} for an account that may sign in. */
  String ACTIVE = "ACTIVE";

  /** Code stored for a locked account. */
  String LOCKED = "LOCKED";

  /** Code stored for a deactivated account. */
  String DISABLED = "DISABLED";

  /**
   * Restores the state from the stored columns.
   *
   * @param status the value of {@code status}
   * @param lockedUntil the value of {@code locked_until}, null if none
   * @return the state
   * @throws IllegalArgumentException if the status is unknown
   */
  static AccountState of(String status, Instant lockedUntil) {
    return switch (status) {
      case ACTIVE -> ActiveAccountState.INSTANCE;
      case LOCKED -> new LockedAccountState(lockedUntil);
      case DISABLED -> DisabledAccountState.INSTANCE;
      default -> throw new IllegalArgumentException("Unknown account status");
    };
  }

  /**
   * The code stored in {@code iam.users.status}.
   *
   * @return the code
   */
  String code();

  /**
   * Tells whether the account may sign in now.
   *
   * @param now the current instant
   * @return true if the password may be checked and a session issued
   */
  boolean allowsLogin(Instant now);

  /**
   * Moves the account to the status an administrator asked for.
   *
   * @param target the code of the wanted status
   * @return the new state; the same state if it already is the wanted one
   * @throws InvalidAccountTransitionException if the change is not allowed
   */
  AccountState changeTo(String target);

  /**
   * End of the lock, if the account is locked by failed logins.
   *
   * @return the end, or null
   */
  default Instant lockedUntil() {
    return null;
  }

  /**
   * Tells whether the change ends the sessions of the account (a lock or a deactivation).
   *
   * @return true if the sessions must be revoked
   */
  default boolean endsSessions() {
    return !allowsLogin(Instant.MIN);
  }
}
