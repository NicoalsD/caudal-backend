package co.caudal.application.port.out;

import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;

/**
 * Counts requests per key in a store shared by every instance of the API (docs/Seguridad.md,
 * section 2.10). Keys never hold personal data in clear: an IP goes as its HMAC.
 */
public interface RateLimiterPort {

  /**
   * Takes one token from the bucket of a key.
   *
   * @param key the bucket, for example {@code login-ip:<hmac>}
   * @param limit the limit of that bucket
   * @return whether the request may go on and, if not, how long to wait
   */
  RateLimitDecision tryConsume(String key, RateLimit limit);
}
