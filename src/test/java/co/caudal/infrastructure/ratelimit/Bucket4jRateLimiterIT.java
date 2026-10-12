package co.caudal.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.support.AppRoleIT;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Buckets in iam.rate_limit_buckets, as caudal_app, shared by every instance. */
class Bucket4jRateLimiterIT extends AppRoleIT {

  @Autowired private RateLimiterPort limiter;

  @Test
  void allowsTheCapacityThenAsksToWaitAndKeepsBucketsApart() {
    String key = "login-ip:" + UUID.randomUUID();
    RateLimit limit = new RateLimit(5, Duration.ofMinutes(1));

    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryConsume(key, limit).allowed()).isTrue();
    }
    RateLimitDecision sixth = limiter.tryConsume(key, limit);

    assertThat(sixth.allowed()).isFalse();
    assertThat(sixth.retryAfterSeconds()).isBetween(1L, 12L);
    assertThat(limiter.tryConsume("login-ip:" + UUID.randomUUID(), limit).allowed()).isTrue();
    assertThat(
            owner.queryForObject(
                "SELECT count(*) FROM iam.rate_limit_buckets WHERE id = ?", Integer.class, key))
        .isEqualTo(1);
  }
}
