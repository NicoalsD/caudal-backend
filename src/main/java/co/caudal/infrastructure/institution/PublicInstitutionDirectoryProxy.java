package co.caudal.infrastructure.institution;

import co.caudal.application.institution.InstitutionListing;
import co.caudal.application.institution.PublicInstitution;
import co.caudal.application.institution.PublicInstitutionDirectory;
import co.caudal.application.port.out.InstitutionDirectoryPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;

/**
 * Stands in for the real directory and decides what the public can see: only the active
 * institutions, and only their {@code slug} and {@code name}. Every call is also limited per
 * address, because the directory needs no login.
 *
 * <p>The real subject ({@link InstitutionDirectoryPort}) returns the full rows, with the
 * municipality and the simulated flag, so the filter lives in one place that is tested on its own.
 *
 * @pattern Proxy. Same interface as the directory the caller uses; it controls access to the real
 *     directory (filter, field selection and rate limit) without changing the caller.
 */
public class PublicInstitutionDirectoryProxy implements PublicInstitutionDirectory {

  private static final String KEY_PREFIX = "public-directory:";
  private static final String UNKNOWN_IP = "unknown";

  private final InstitutionDirectoryPort subject;
  private final RateLimiterPort rateLimiter;
  private final PrivacyHasherPort hasher;
  private final RateLimit limit;

  /**
   * Creates the proxy.
   *
   * @param subject the real directory
   * @param rateLimiter shared rate limiter
   * @param hasher keyed hash of the address
   * @param limit requests per address ({@code RATE_LIMIT_PUBLIC_DIRECTORY_PER_MINUTE})
   */
  public PublicInstitutionDirectoryProxy(
      InstitutionDirectoryPort subject,
      RateLimiterPort rateLimiter,
      PrivacyHasherPort hasher,
      RateLimit limit) {
    this.subject = subject;
    this.rateLimiter = rateLimiter;
    this.hasher = hasher;
    this.limit = limit;
  }

  @Override
  public List<PublicInstitution> list(String ipAddress) {
    String ipHmac = hasher.hmac(ipAddress == null ? UNKNOWN_IP : ipAddress);
    RateLimitDecision decision = rateLimiter.tryConsume(KEY_PREFIX + ipHmac, limit);
    if (!decision.allowed()) {
      throw new DomainException(
          ErrorCode.RATE_LIMITED, Map.of("retry_after_seconds", decision.retryAfterSeconds()));
    }
    return subject.listAll().stream()
        .filter(InstitutionListing::active)
        .map(i -> new PublicInstitution(i.slug(), i.name()))
        .toList();
  }
}
