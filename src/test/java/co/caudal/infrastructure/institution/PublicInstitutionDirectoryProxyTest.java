package co.caudal.infrastructure.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.caudal.application.institution.InstitutionListing;
import co.caudal.application.institution.PublicInstitution;
import co.caudal.application.port.out.InstitutionDirectoryPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.ratelimit.RateLimitDecision;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicInstitutionDirectoryProxyTest {

  private static final RateLimit LIMIT = new RateLimit(30, Duration.ofMinutes(1));

  private final InstitutionDirectoryPort subject = mock(InstitutionDirectoryPort.class);
  private final RateLimiterPort rateLimiter = mock(RateLimiterPort.class);
  private final PrivacyHasherPort hasher = mock(PrivacyHasherPort.class);
  private final PublicInstitutionDirectoryProxy proxy =
      new PublicInstitutionDirectoryProxy(subject, rateLimiter, hasher, LIMIT);

  @Test
  void keepsOnlyActiveInstitutionsAndOnlySlugAndName() {
    when(rateLimiter.tryConsume(anyString(), any())).thenReturn(RateLimitDecision.allow());
    when(hasher.hmac(anyString())).thenReturn("ip-hmac");
    when(subject.listAll())
        .thenReturn(
            List.of(
                new InstitutionListing("vereda-norte", "Vereda Norte", "Guaitarilla", false, true),
                new InstitutionListing("vereda-sur", "Vereda Sur", "Guaitarilla", false, false),
                new InstitutionListing(
                    "maqueta-caudal", "Maqueta CAUDAL", "Guaitarilla", true, true)));

    List<PublicInstitution> visible = proxy.list("203.0.113.9");

    assertThat(visible)
        .containsExactly(
            new PublicInstitution("vereda-norte", "Vereda Norte"),
            new PublicInstitution("maqueta-caudal", "Maqueta CAUDAL"));
  }

  @Test
  void answersRateLimitedOverTheLimit() {
    when(rateLimiter.tryConsume(anyString(), any()))
        .thenReturn(new RateLimitDecision(false, Duration.ofSeconds(12)));
    when(hasher.hmac(anyString())).thenReturn("ip-hmac");

    assertThatThrownBy(() -> proxy.list("203.0.113.9"))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.RATE_LIMITED));
  }
}
