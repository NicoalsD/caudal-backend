package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.Membership;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshSessionUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final ClientContext CLIENT = new ClientContext("ip-hmac", "JUnit");

  private final InMemoryRefreshTokenPort store = new InMemoryRefreshTokenPort();
  private final RecordingSecurityEvents events = new RecordingSecurityEvents();
  private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RefreshTokenService tokens =
      new RefreshTokenService(store, events, unitOfWork, clock);
  private final TokenVersionPort versions = mock(TokenVersionPort.class);
  private final MembershipPort memberships = mock(MembershipPort.class);
  private final AccessTokenIssuerPort accessTokens = mock(AccessTokenIssuerPort.class);
  private final PrivacyHasherPort hasher = mock(PrivacyHasherPort.class);
  private final RefreshSessionUseCase useCase =
      new RefreshSessionUseCase(
          tokens, versions, memberships, accessTokens, hasher, unitOfWork, clock);

  private String sessionSecret() {
    return tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT).rawValue();
  }

  private void givenActiveAccount() {
    when(hasher.hmac(any())).thenReturn("ip-hmac");
    when(versions.findCurrentVersion(USER)).thenReturn(OptionalInt.of(4));
    when(memberships.findActive(eq(USER), any()))
        .thenReturn(List.of(new Membership(AQUEDUCT, "BOARD_MEMBER", NOW.minusSeconds(60), null)));
    when(accessTokens.issue(any()))
        .thenReturn(new IssuedAccessToken("new-jwt", Duration.ofMinutes(15)));
  }

  @Test
  void rotatesTheRefreshTokenAndIssuesAFreshAccessToken() {
    givenActiveAccount();
    String secret = sessionSecret();

    RefreshResult result = useCase.execute(new RefreshInput(secret, "203.0.113.9", "JUnit"));

    assertThat(result.accessToken().value()).isEqualTo("new-jwt");
    assertThat(result.refreshToken().rawValue()).isNotEqualTo(secret);
    ArgumentCaptor<AccessTokenSubject> subject = ArgumentCaptor.forClass(AccessTokenSubject.class);
    verify(accessTokens).issue(subject.capture());
    assertThat(subject.getValue())
        .isEqualTo(new AccessTokenSubject(USER, "BOARD_MEMBER", AQUEDUCT, 4));
  }

  @Test
  void theNewAccessTokenCarriesTheCurrentTokenVersionNotAnOldOne() {
    givenActiveAccount();
    when(versions.findCurrentVersion(USER)).thenReturn(OptionalInt.of(9));

    useCase.execute(new RefreshInput(sessionSecret(), "203.0.113.9", "JUnit"));

    ArgumentCaptor<AccessTokenSubject> subject = ArgumentCaptor.forClass(AccessTokenSubject.class);
    verify(accessTokens).issue(subject.capture());
    assertThat(subject.getValue().tokenVersion()).isEqualTo(9);
  }

  @Test
  void aMissingCookieIsUnauthorized() {
    givenActiveAccount();

    assertThatThrownBy(() -> useCase.execute(new RefreshInput(null, "203.0.113.9", "JUnit")))
        .isInstanceOf(UnauthorizedException.class);
    verify(accessTokens, never()).issue(any());
  }

  @Test
  void aReusedTokenIsASessionRevoked() {
    givenActiveAccount();
    String secret = sessionSecret();
    useCase.execute(new RefreshInput(secret, "203.0.113.9", "JUnit"));

    assertThatThrownBy(() -> useCase.execute(new RefreshInput(secret, "203.0.113.9", "JUnit")))
        .isInstanceOf(SessionRevokedException.class);
  }

  @Test
  void aDisabledAccountCannotRefresh() {
    givenActiveAccount();
    when(versions.findCurrentVersion(USER)).thenReturn(OptionalInt.empty());

    assertThatThrownBy(
            () -> useCase.execute(new RefreshInput(sessionSecret(), "203.0.113.9", "JUnit")))
        .isInstanceOf(UnauthorizedException.class);
    verify(accessTokens, never()).issue(any());
  }

  @Test
  void anAccountWithoutMembershipInForceCannotRefresh() {
    givenActiveAccount();
    when(memberships.findActive(eq(USER), any())).thenReturn(List.of());

    assertThatThrownBy(
            () -> useCase.execute(new RefreshInput(sessionSecret(), "203.0.113.9", "JUnit")))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void inputToStringDoesNotShowTheSecret() {
    assertThat(new RefreshInput("the-secret-value", "203.0.113.9", "JUnit").toString())
        .doesNotContain("the-secret-value");
  }
}
