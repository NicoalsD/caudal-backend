package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.TokenVersionPort;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Persistence adapter of the accounts ({@code iam.users}). */
@Component
public class UserPersistenceAdapter implements TokenVersionPort {

  /** Status of an account that was deactivated for good. */
  static final String STATUS_DISABLED = "DISABLED";

  private final UserJpaRepository users;

  UserPersistenceAdapter(UserJpaRepository users) {
    this.users = users;
  }

  @Override
  public OptionalInt findCurrentVersion(UUID userId) {
    return users
        .findTokenVersionUnlessDisabled(userId, STATUS_DISABLED)
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }
}
