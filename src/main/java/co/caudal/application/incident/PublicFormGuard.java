package co.caudal.application.incident;

import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Protects the public form against abuse (docs/Seguridad.md): a limit of reports per address and
 * hour, a minimum time between opening the form and sending it, and a hidden field people never
 * fill. A filled hidden field is recorded as {@code HONEYPOT_TRIGGERED} and the sender still gets a
 * normal answer, so it learns nothing.
 */
public class PublicFormGuard {

  private static final String KEY_PREFIX = "public-report:";
  private static final String FORM_STARTED_AT = "form_started_at";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private final RateLimiterPort rateLimiter;
  private final SecurityEventPort events;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;
  private final RateLimit limit;
  private final Duration minFillTime;

  /**
   * Creates the guard.
   *
   * @param rateLimiter shared rate limiter
   * @param events security events
   * @param hasher HMAC of the address
   * @param unitOfWork transaction boundary
   * @param clock source of time
   * @param limit reports per address ({@code RATE_LIMIT_PUBLIC_REPORTS_PER_HOUR})
   * @param minFillTime shortest time to fill the form ({@code PUBLIC_FORM_MIN_SECONDS})
   */
  @SuppressWarnings("checkstyle:ParameterNumber")
  public PublicFormGuard(
      RateLimiterPort rateLimiter,
      SecurityEventPort events,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      Clock clock,
      RateLimit limit,
      Duration minFillTime) {
    this.rateLimiter = rateLimiter;
    this.events = events;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
    this.limit = limit;
    this.minFillTime = minFillTime;
  }

  /**
   * Decides whether a report is stored.
   *
   * @param aqueductId the aqueduct
   * @param ipAddress the address of the request
   * @param website the hidden field
   * @param formStartedAt when the form was opened
   * @return true to store it, false when the hidden field was filled
   * @throws DomainException RATE_LIMITED over the limit, or a validation error for a form sent too
   *     fast or dated in the future
   */
  public boolean admit(UUID aqueductId, String ipAddress, String website, Instant formStartedAt) {
    String ipHmac = ipAddress == null ? null : hasher.hmac(ipAddress);
    RateLimitDecision decision =
        rateLimiter.tryConsume(KEY_PREFIX + (ipHmac == null ? "unknown" : ipHmac), limit);
    if (!decision.allowed()) {
      throw new DomainException(
          ErrorCode.RATE_LIMITED, Map.of("retry_after_seconds", decision.retryAfterSeconds()));
    }
    if (website != null && !website.isBlank()) {
      unitOfWork.execute(
          () -> {
            events.record(
                new SecurityEvent(
                    SecurityEventType.HONEYPOT_TRIGGERED,
                    Severity.MEDIUM,
                    null,
                    aqueductId,
                    ipHmac,
                    Map.of("form", "damage-report")));
            return null;
          });
      return false;
    }
    if (formStartedAt == null) {
      throw new FieldValidationException(FORM_STARTED_AT, "FIELD_REQUIRED");
    }
    Instant now = clock.instant();
    boolean tooFast = formStartedAt.plus(minFillTime).isAfter(now);
    boolean future =
        formStartedAt.isAfter(now.plusSeconds(FieldLimits.FUTURE_TIMESTAMP_SKEW_SECONDS));
    if (tooFast || future) {
      throw new FieldValidationException(FORM_STARTED_AT, NOT_ALLOWED);
    }
    return true;
  }
}
