package co.caudal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.user.LockoutPolicy.LockoutDecision;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockoutPolicyTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private final LockoutPolicy policy =
      new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24));

  @Test
  void fourFailuresDoNotLock() {
    LockoutDecision decision = policy.evaluate(4, 0, NOW);

    assertThat(decision.locked()).isFalse();
    assertThat(decision.lockedUntil()).isNull();
    assertThat(decision.level()).isZero();
  }

  @Test
  void theFifthFailureLocksForFifteenMinutes() {
    LockoutDecision decision = policy.evaluate(5, 0, NOW);

    assertThat(decision.locked()).isTrue();
    assertThat(decision.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(decision.level()).isEqualTo(1);
  }

  @Test
  void eachNewLockDoublesTheLast() {
    assertThat(policy.evaluate(5, 1, NOW).lockedUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    assertThat(policy.evaluate(5, 2, NOW).lockedUntil())
        .isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    assertThat(policy.evaluate(5, 3, NOW).level()).isEqualTo(4);
  }

  @Test
  void theLockNeverExceedsTwentyFourHours() {
    assertThat(policy.evaluate(5, 9, NOW).lockedUntil()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(policy.evaluate(5, 500, NOW).lockedUntil())
        .isEqualTo(NOW.plus(Duration.ofHours(24)));
  }

  @Test
  void moreFailuresThanTheMaximumStillLock() {
    assertThat(policy.evaluate(9, 0, NOW).locked()).isTrue();
  }

  @Test
  void invalidSettingsAreRefused() {
    assertThatThrownBy(() -> new LockoutPolicy(0, Duration.ofMinutes(1), Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LockoutPolicy(5, Duration.ZERO, Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LockoutPolicy(5, Duration.ofHours(2), Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
