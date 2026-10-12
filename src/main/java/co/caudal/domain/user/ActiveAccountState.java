package co.caudal.domain.user;

import java.time.Instant;

/**
 * An account that may sign in.
 *
 * @pattern P18 State
 */
public final class ActiveAccountState implements AccountState {

  static final ActiveAccountState INSTANCE = new ActiveAccountState();

  private ActiveAccountState() {}

  @Override
  public String code() {
    return ACTIVE;
  }

  @Override
  public boolean allowsLogin(Instant now) {
    return true;
  }

  @Override
  public AccountState changeTo(String target) {
    return switch (target) {
      case ACTIVE -> this;
      case LOCKED -> new LockedAccountState(null);
      case DISABLED -> DisabledAccountState.INSTANCE;
      default -> throw new InvalidAccountTransitionException(ACTIVE, target);
    };
  }
}
