package co.caudal.infrastructure.ratelimit;

import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;

/**
 * Token buckets of Bucket4j stored in {@code iam.rate_limit_buckets}, so every instance of the API
 * shares the same counts (docs/Seguridad.md, section 2.10). PostgreSQL advisory locks serialize the
 * updates of one bucket.
 *
 * @pattern P06 Adapter
 */
public class Bucket4jRateLimiterAdapter implements RateLimiterPort {

  private final ProxyManager<String> buckets;

  /**
   * Creates the adapter.
   *
   * @param buckets the Bucket4j proxy manager over PostgreSQL
   */
  public Bucket4jRateLimiterAdapter(ProxyManager<String> buckets) {
    this.buckets = buckets;
  }

  @Override
  public RateLimitDecision tryConsume(String key, RateLimit limit) {
    BucketConfiguration configuration =
        BucketConfiguration.builder()
            .addLimit(
                bandwidth ->
                    bandwidth
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period()))
            .build();
    ConsumptionProbe probe =
        buckets.builder().build(key, () -> configuration).tryConsumeAndReturnRemaining(1);
    return probe.isConsumed()
        ? RateLimitDecision.allow()
        : new RateLimitDecision(false, Duration.ofNanos(probe.getNanosToWaitForRefill()));
  }
}
