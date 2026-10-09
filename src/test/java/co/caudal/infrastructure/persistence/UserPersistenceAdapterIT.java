package co.caudal.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.support.AppRoleIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserPersistenceAdapterIT extends AppRoleIT {

  @Autowired private UserPersistenceAdapter users;

  private String uniqueName() {
    return "user." + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void readsTheTokenVersionOfAnActiveAccount() {
    UUID user = newUser(uniqueName(), UNUSABLE_HASH);
    owner.update("UPDATE iam.users SET token_version = 7 WHERE id = ?", user);

    assertThat(users.findCurrentVersion(user)).hasValue(7);
  }

  @Test
  void followsTheVersionWhenItIsRaised() {
    UUID user = newUser(uniqueName(), UNUSABLE_HASH);
    owner.update("UPDATE iam.users SET token_version = token_version + 1 WHERE id = ?", user);

    assertThat(users.findCurrentVersion(user)).hasValue(1);
  }

  @Test
  void aDisabledAccountHasNoSession() {
    UUID user = newUser(uniqueName(), UNUSABLE_HASH);
    owner.update(
        "UPDATE iam.users SET status = 'DISABLED', disabled_at = now() WHERE id = ?", user);

    assertThat(users.findCurrentVersion(user)).isEmpty();
  }

  @Test
  void aLockedAccountStillHasItsVersion() {
    UUID user = newUser(uniqueName(), UNUSABLE_HASH);
    owner.update("UPDATE iam.users SET status = 'LOCKED' WHERE id = ?", user);

    assertThat(users.findCurrentVersion(user)).hasValue(0);
  }

  @Test
  void anUnknownAccountHasNoSession() {
    assertThat(users.findCurrentVersion(UUID.randomUUID())).isEmpty();
  }
}
