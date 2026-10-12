package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountStateTest {

  private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

  @Test
  void restoresEachStoredStatus() {
    assertThat(AccountState.of("ACTIVE", null)).isInstanceOf(ActiveAccountState.class);
    assertThat(AccountState.of("LOCKED", NOW)).isInstanceOf(LockedAccountState.class);
    assertThat(AccountState.of("DISABLED", null)).isInstanceOf(DisabledAccountState.class);
    assertThat(AccountState.of("LOCKED", NOW).lockedUntil()).isEqualTo(NOW);
    assertThat(AccountState.of("ACTIVE", null).lockedUntil()).isNull();
    assertThatThrownBy(() -> AccountState.of("GONE", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anActiveAccountSignsIn() {
    AccountState state = AccountState.of("ACTIVE", null);

    assertThat(state.allowsLogin(NOW)).isTrue();
    assertThat(state.code()).isEqualTo("ACTIVE");
    assertThat(state.endsSessions()).isFalse();
  }

  @Test
  void aLockFromFailedLoginsEndsByItself() {
    AccountState state = AccountState.of("LOCKED", NOW.plusSeconds(900));

    assertThat(state.allowsLogin(NOW)).isFalse();
    assertThat(state.allowsLogin(NOW.plusSeconds(900))).isTrue();
    assertThat(state.endsSessions()).isTrue();
  }

  @Test
  void aLockFromAnAdministratorLastsUntilUnlocked() {
    AccountState state = AccountState.of("ACTIVE", null).changeTo("LOCKED");

    assertThat(state.code()).isEqualTo("LOCKED");
    assertThat(state.lockedUntil()).isNull();
    assertThat(state.allowsLogin(Instant.MAX)).isFalse();
    assertThat(state.changeTo("ACTIVE").allowsLogin(NOW)).isTrue();
  }

  @Test
  void aDisabledAccountNeverSignsInNorComesBack() {
    AccountState state = AccountState.of("LOCKED", null).changeTo("DISABLED");

    assertThat(state.code()).isEqualTo("DISABLED");
    assertThat(state.allowsLogin(NOW)).isFalse();
    assertThat(state.endsSessions()).isTrue();
    assertThat(state.changeTo("DISABLED")).isSameAs(state);
    assertThatThrownBy(() -> state.changeTo("ACTIVE"))
        .isInstanceOf(InvalidAccountTransitionException.class)
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    assertThatThrownBy(() -> state.changeTo("LOCKED"))
        .isInstanceOf(InvalidAccountTransitionException.class);
  }

  @Test
  void changesToTheSameStatusKeepTheState() {
    AccountState active = AccountState.of("ACTIVE", null);

    assertThat(active.changeTo("ACTIVE")).isSameAs(active);
    assertThat(active.changeTo("DISABLED").code()).isEqualTo("DISABLED");
    assertThat(AccountState.of("LOCKED", NOW).changeTo("LOCKED").lockedUntil()).isNull();
    assertThat(AccountState.of("LOCKED", NOW).changeTo("DISABLED").code()).isEqualTo("DISABLED");
  }

  @Test
  void rejectsAnUnknownTarget() {
    assertThatThrownBy(() -> AccountState.of("ACTIVE", null).changeTo("PAUSED"))
        .isInstanceOf(InvalidAccountTransitionException.class);
    assertThatThrownBy(() -> AccountState.of("LOCKED", null).changeTo("PAUSED"))
        .isInstanceOf(InvalidAccountTransitionException.class);
  }
}
