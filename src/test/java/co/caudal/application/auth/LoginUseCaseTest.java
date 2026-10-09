package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.LoginAttempt.FailureReason;
import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.LoginAttemptPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.user.InvalidCredentialsException;
import co.caudal.domain.user.LockoutPolicy;
import co.caudal.domain.user.Membership;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final String IP_HMAC = "ip-hmac";

  private final LoginAccountPort accounts = mock(LoginAccountPort.class);
  private final LoginAttemptPort attempts = mock(LoginAttemptPort.class);
  private final MembershipPort memberships = mock(MembershipPort.class);
  private final PasswordHasherPort passwords = mock(PasswordHasherPort.class);
  private final PrivacyHasherPort hasher = mock(PrivacyHasherPort.class);
  private final AccessTokenIssuerPort accessTokens = mock(AccessTokenIssuerPort.class);
  private final RecordingSecurityEvents events = new RecordingSecurityEvents();
  private final InMemoryRefreshTokenPort tokenStore = new InMemoryRefreshTokenPort();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();

  private final LoginUseCase useCase =
      new LoginUseCase(
          new LoginPorts(
              accounts,
              attempts,
              memberships,
              passwords,
              hasher,
              accessTokens,
              new RefreshTokenService(tokenStore, events, unitOfWork, clock),
              events,
              unitOfWork),
          new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofHours(24)),
          new LoginSettings(
              Duration.ofMinutes(15),
              20,
              Duration.ofMinutes(15),
              role -> "OPERATOR".equals(role) ? Duration.ofDays(30) : Duration.ofDays(7)),
          clock);

  private LoginAccount account(String status, Instant lockedUntil, int level) {
    return new LoginAccount(
        USER,
        "test.operator",
        "Persona de Prueba Uno",
        "stored-hash",
        status,
        lockedUntil,
        level,
        3,
        false);
  }

  private LoginInput input(String username, String password) {
    return new LoginInput(username, password, "203.0.113.9", "JUnit");
  }

  private void givenWorld(LoginAccount account, boolean passwordMatches) {
    when(hasher.hmac(anyString())).thenAnswer(call -> "hmac:" + call.getArgument(0));
    when(hasher.hmac("203.0.113.9")).thenReturn(IP_HMAC);
    when(accounts.findByUsername("test.operator")).thenReturn(Optional.ofNullable(account));
    when(passwords.matches(any(), anyString())).thenReturn(passwordMatches);
    when(memberships.findActive(eq(USER), any()))
        .thenReturn(List.of(new Membership(AQUEDUCT, "OPERATOR", NOW.minusSeconds(60), null)));
    when(accessTokens.issue(any()))
        .thenReturn(new IssuedAccessToken("jwt-value", Duration.ofMinutes(15)));
  }

  private LoginAttempt lastAttempt() {
    ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
    verify(attempts).record(captor.capture());
    return captor.getValue();
  }

  @Test
  void signsInAndIssuesBothTokens() {
    givenWorld(account("ACTIVE", null, 0), true);

    LoginResult result = useCase.execute(input("  Test.Operator ", "clave-correcta-larga"));

    assertThat(result.accessToken().value()).isEqualTo("jwt-value");
    assertThat(result.refreshToken().timeToLive()).isEqualTo(Duration.ofDays(30));
    assertThat(result.user().role()).isEqualTo("OPERATOR");
    assertThat(result.user().aqueductId()).isEqualTo(AQUEDUCT);
    ArgumentCaptor<AccessTokenSubject> subject = ArgumentCaptor.forClass(AccessTokenSubject.class);
    verify(accessTokens).issue(subject.capture());
    assertThat(subject.getValue().tokenVersion()).isEqualTo(3);
    verify(accounts).saveSuccess(USER, NOW, IP_HMAC);
    assertThat(lastAttempt().succeeded()).isTrue();
    assertThat(tokenStore.tokens).hasSize(1);
  }

  @Test
  void nonOperatorsGetTheDefaultSessionLength() {
    givenWorld(account("ACTIVE", null, 0), true);
    when(memberships.findActive(eq(USER), any()))
        .thenReturn(List.of(new Membership(AQUEDUCT, "BOARD_ADMIN", NOW.minusSeconds(60), null)));

    assertThat(
            useCase
                .execute(input("test.operator", "clave-correcta-larga"))
                .refreshToken()
                .timeToLive())
        .isEqualTo(Duration.ofDays(7));
  }

  @Test
  void aWrongPasswordIsAGenericFailureThatIsRecordedWithHmacs() {
    givenWorld(account("ACTIVE", null, 0), false);
    when(attempts.countConsecutiveFailures(eq(USER), any())).thenReturn(1);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-incorrecta")))
        .isInstanceOf(InvalidCredentialsException.class);

    LoginAttempt attempt = lastAttempt();
    assertThat(attempt.failureReason()).isEqualTo(FailureReason.INVALID_CREDENTIALS);
    assertThat(attempt.ipHmac()).isEqualTo(IP_HMAC).doesNotContain("203.0.113.9");
    assertThat(attempt.usernameHmac()).startsWith("hmac:").doesNotContain("203.0.113.9");
    verify(accounts).saveFailure(USER, false, 1, null, 0);
    verify(accessTokens, never()).issue(any());
  }

  @Test
  void anUnknownUserSpendsTheDecoyAndGetsTheSameError() {
    givenWorld(null, false);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-incorrecta")))
        .isInstanceOf(InvalidCredentialsException.class)
        .extracting(ex -> ((DomainException) ex).code())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

    verify(passwords).verifyAgainstDecoy(any());
    verify(passwords, never()).matches(any(), anyString());
    assertThat(lastAttempt().userId()).isNull();
  }

  @Test
  void aUsernameWithABadFormatIsTreatedAsUnknown() {
    givenWorld(account("ACTIVE", null, 0), true);

    assertThatThrownBy(() -> useCase.execute(input("no es un usuario!", "clave-correcta-larga")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(accounts, never()).findByUsername(anyString());
    verify(passwords).verifyAgainstDecoy(any());
  }

  @Test
  void aDisabledAccountFailsGenericallyWithoutCheckingThePassword() {
    givenWorld(account("DISABLED", null, 0), true);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-correcta-larga")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwords, never()).matches(any(), anyString());
    verify(passwords).verifyAgainstDecoy(any());
    assertThat(lastAttempt().failureReason()).isEqualTo(FailureReason.DISABLED);
  }

  @Test
  void aLockedAccountFailsEvenWithTheRightPassword() {
    givenWorld(account("LOCKED", NOW.plusSeconds(600), 1), true);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-correcta-larga")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwords, never()).matches(any(), anyString());
    assertThat(lastAttempt().failureReason()).isEqualTo(FailureReason.LOCKED);
  }

  @Test
  void anExpiredLockDoesNotBlockTheLogin() {
    givenWorld(account("LOCKED", NOW.minusSeconds(1), 1), true);

    assertThat(useCase.execute(input("test.operator", "clave-correcta-larga")).accessToken())
        .isNotNull();
  }

  @Test
  void theFifthConsecutiveFailureLocksTheAccountAndRecordsAnEvent() {
    givenWorld(account("ACTIVE", null, 0), false);
    when(attempts.countConsecutiveFailures(eq(USER), any())).thenReturn(5);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-incorrecta")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(accounts).saveFailure(USER, true, 5, NOW.plus(Duration.ofMinutes(15)), 1);
    assertThat(events.recorded)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.type()).isEqualTo(SecurityEventType.ACCOUNT_LOCKED);
              assertThat(event.actorUserId()).isEqualTo(USER);
            });
  }

  @Test
  void theSecondLockLastsThirtyMinutes() {
    givenWorld(account("ACTIVE", null, 1), false);
    when(attempts.countConsecutiveFailures(eq(USER), any())).thenReturn(5);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-incorrecta")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(accounts).saveFailure(USER, true, 5, NOW.plus(Duration.ofMinutes(30)), 2);
  }

  @Test
  void anIpWithTooManyFailuresIsRateLimitedBeforeLookingAtThePassword() {
    givenWorld(account("ACTIVE", null, 0), true);
    when(attempts.countFailuresByIp(eq(IP_HMAC), any())).thenReturn(20);

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-correcta-larga")))
        .isInstanceOfSatisfying(
            DomainException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RATE_LIMITED));

    verify(accounts, never()).findByUsername(anyString());
    verify(passwords, never()).matches(any(), anyString());
    assertThat(lastAttempt().failureReason()).isEqualTo(FailureReason.RATE_LIMITED);
    assertThat(events.recorded)
        .singleElement()
        .satisfies(event -> assertThat(event.type()).isEqualTo(SecurityEventType.RATE_LIMITED));
  }

  @Test
  void anAccountWithoutAMembershipInForceCannotSignIn() {
    givenWorld(account("ACTIVE", null, 0), true);
    when(memberships.findActive(eq(USER), any())).thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute(input("test.operator", "clave-correcta-larga")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(accessTokens, never()).issue(any());
    verify(accounts, never()).saveSuccess(any(), any(), anyString());
    assertThat(tokenStore.tokens).isEmpty();
  }

  @Test
  void aHashMadeWithOldParametersIsReplacedAfterASuccessfulLogin() {
    givenWorld(account("ACTIVE", null, 0), true);
    when(passwords.needsRehash("stored-hash")).thenReturn(true);
    when(passwords.hash(any())).thenReturn("new-hash");

    useCase.execute(input("test.operator", "clave-correcta-larga"));

    verify(accounts).updatePasswordHash(USER, "new-hash");
  }

  @Test
  void aCurrentHashIsLeftAlone() {
    givenWorld(account("ACTIVE", null, 0), true);

    useCase.execute(input("test.operator", "clave-correcta-larga"));

    verify(accounts, never()).updatePasswordHash(any(), anyString());
  }

  @Test
  void overlongUsernameInputIsBoundedBeforeHashing() {
    givenWorld(null, false);

    assertThatThrownBy(() -> useCase.execute(input("a".repeat(5000), "clave-incorrecta")))
        .isInstanceOf(InvalidCredentialsException.class);

    ArgumentCaptor<String> hashed = ArgumentCaptor.forClass(String.class);
    verify(hasher, org.mockito.Mockito.atLeastOnce()).hmac(hashed.capture());
    assertThat(hashed.getAllValues())
        .allSatisfy(value -> assertThat(value.length()).isLessThanOrEqualTo(128));
  }

  @Test
  void inputToStringDoesNotShowThePassword() {
    assertThat(input("test.operator", "super-secreto-largo").toString())
        .doesNotContain("super-secreto-largo");
  }
}
