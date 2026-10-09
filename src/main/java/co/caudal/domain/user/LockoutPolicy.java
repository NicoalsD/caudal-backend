package co.caudal.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * When and for how long an account is locked after failed logins (docs/Seguridad.md, section 2.2).
 *
 * <p>{@code maxFailures} consecutive failures inside the window lock the account for {@code
 * baseLock}. Each new lock doubles the previous one, up to {@code maxLock} (24 hours).
 *
 * @param maxFailures consecutive failures that cause a lock
 * @param baseLock length of the first lock
 * @param maxLock longest lock
 */
public record LockoutPolicy(int maxFailures, Duration baseLock, Duration maxLock) {

  /** Validates the settings. */
  public LockoutPolicy {
    Objects.requireNonNull(baseLock, "baseLock");
    Objects.requireNonNull(maxLock, "maxLock");
    if (maxFailures < 1
        || baseLock.isZero()
        || baseLock.isNegative()
        || maxLock.compareTo(baseLock) < 0) {
      throw new IllegalArgumentException("Invalid lockout settings");
    }
  }

  /**
   * Decides what happens after a failed attempt.
   *
   * @param failuresInWindow consecutive failures in the window, counting the one that just happened
   * @param currentLevel how many locks the account already had
   * @param now the current instant
   * @return the decision
   */
  public LockoutDecision evaluate(int failuresInWindow, int currentLevel, Instant now) {
    if (failuresInWindow < maxFailures) {
      return LockoutDecision.notLocked(currentLevel);
    }
    Duration duration = baseLock;
    for (int level = 0; level < currentLevel && duration.compareTo(maxLock) < 0; level++) {
      duration = duration.multipliedBy(2);
    }
    if (duration.compareTo(maxLock) > 0) {
      duration = maxLock;
    }
    return new LockoutDecision(true, now.plus(duration), currentLevel + 1);
  }

  /**
   * Result of {@link #evaluate}.
   *
   * @param locked true if the account must be locked now
   * @param lockedUntil end of the lock, null if not locked
   * @param level lock level after the decision
   */
  public record LockoutDecision(boolean locked, Instant lockedUntil, int level) {

    static LockoutDecision notLocked(int level) {
      return new LockoutDecision(false, null, level);
    }
  }
}
