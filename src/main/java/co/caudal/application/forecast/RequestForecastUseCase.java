package co.caudal.application.forecast;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * A person asks to forecast a tank now (docs/API.md, section 8.3; HU-15, HU-16). Recomputing is
 * limited per aqueduct ({@code RATE_LIMIT_FORECAST_PER_HOUR}), so the service is never flooded; the
 * answer is always a forecast, from the service or from the simple estimate.
 */
public class RequestForecastUseCase {

  private static final String KEY_PREFIX = "forecast-aqueduct:";

  private final ForecastRunner runner;
  private final RateLimiterPort rateLimiter;
  private final RateLimit limit;

  /**
   * Creates the use case.
   *
   * @param runner forecasts and stores
   * @param rateLimiter shared rate limits
   * @param limit forecasts per aqueduct per period
   */
  public RequestForecastUseCase(
      ForecastRunner runner, RateLimiterPort rateLimiter, RateLimit limit) {
    this.runner = runner;
    this.rateLimiter = rateLimiter;
    this.limit = limit;
  }

  /**
   * Forecasts a tank.
   *
   * @param caller who asks
   * @param tankId the tank
   * @param horizonDays days to forecast, or null for the rules
   * @return the primary run
   * @throws DomainException with {@code RATE_LIMITED} and {@code retry_after_seconds} over the
   *     limit
   */
  public StoredForecast execute(Caller caller, UUID tankId, Integer horizonDays) {
    RateLimitDecision decision = rateLimiter.tryConsume(KEY_PREFIX + caller.aqueductId(), limit);
    if (!decision.allowed()) {
      throw new DomainException(
          ErrorCode.RATE_LIMITED, Map.of("retry_after_seconds", decision.retryAfterSeconds()));
    }
    return runner.run(caller, tankId, horizonDays);
  }
}
