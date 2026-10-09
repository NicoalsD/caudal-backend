package co.caudal.application.user;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings of the one-time codes, from the environment.
 *
 * @param timeToLive how long a code works after it is issued
 */
public record ResetGrantSettings(Duration timeToLive) {

  /** Validates the settings. */
  public ResetGrantSettings {
    Objects.requireNonNull(timeToLive, "timeToLive");
    if (timeToLive.isZero() || timeToLive.isNegative()) {
      throw new IllegalArgumentException("The code lifetime must be positive");
    }
  }
}
