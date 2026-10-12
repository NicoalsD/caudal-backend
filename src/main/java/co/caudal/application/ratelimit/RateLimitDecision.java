package co.caudal.application.ratelimit;

import java.time.Duration;

/**
 * Whether a request may go on.
 *
 * @param allowed true if a token was taken
 * @param retryAfter how long to wait before the next token, zero when allowed
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

  /**
   * The request may go on.
   *
   * @return an allowed decision
   */
  public static RateLimitDecision allow() {
    return new RateLimitDecision(true, Duration.ZERO);
  }

  /**
   * Seconds to send in {@code Retry-After}, rounded up and at least one.
   *
   * @return the seconds
   */
  public long retryAfterSeconds() {
    long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
    return Math.max(1, seconds);
  }
}
