package co.caudal.domain.user;

import java.time.Instant;

/**
 * A locked account: for a while after failed logins, or until an administrator unlocks it.
 *
 * @pattern P18 State
 */
public final class LockedAccountState implements AccountState {

  private final Instant until;

  /**
   * Creates the state.
   *
   * @param until end of a lock caused by failed logins; null for a lock set by an administrator
   */
  LockedAccountState(Instant until) {
    this.until = until;
  }

  @Override
  public String code() {
    return LOCKED;
  }

  @Override
  public boolean allowsLogin(Instant now) {
    return until != null && !until.isAfter(now);
  }

  @Override
  public AccountState changeTo(String target) {
    return switch (target) {
      case ACTIVE -> ActiveAccountState.INSTANCE;
      case LOCKED -> new LockedAccountState(null);
      case DISABLED -> DisabledAccountState.INSTANCE;
      default -> throw new InvalidAccountTransitionException(LOCKED, target);
    };
  }

  @Override
  public Instant lockedUntil() {
    return until;
  }

  @Override
  public boolean endsSessions() {
    return true;
  }
}
