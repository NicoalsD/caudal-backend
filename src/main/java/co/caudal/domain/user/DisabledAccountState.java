package co.caudal.domain.user;

import java.time.Instant;

/**
 * A deactivated account. It never signs in again and no administrator can bring it back: the person
 * gets a new account, so the history of the old one stays as it was.
 *
 * @pattern P18 State
 */
public final class DisabledAccountState implements AccountState {

  static final DisabledAccountState INSTANCE = new DisabledAccountState();

  private DisabledAccountState() {}

  @Override
  public String code() {
    return DISABLED;
  }

  @Override
  public boolean allowsLogin(Instant now) {
    return false;
  }

  @Override
  public AccountState changeTo(String target) {
    if (DISABLED.equals(target)) {
      return this;
    }
    throw new InvalidAccountTransitionException(DISABLED, target);
  }
}
