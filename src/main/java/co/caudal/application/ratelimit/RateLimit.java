package co.caudal.application.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * A limit of requests: {@code capacity} per {@code period}, refilled gradually.
 *
 * @param capacity requests allowed in a period
 * @param period length of the period
 */
public record RateLimit(int capacity, Duration period) {

  /** Validates the limit. */
  public RateLimit {
    Objects.requireNonNull(period, "period");
    if (capacity < 1 || period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("Invalid rate limit");
    }
  }
}
