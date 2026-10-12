package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.Membership;
import co.caudal.domain.user.PasswordPolicy;
import co.caudal.domain.user.PasswordPolicyViolationException;
import co.caudal.domain.user.ResetCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResetPasswordCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID AQUEDUCT = UUID.randomUUID();
  private static final String NEW_PASSWORD = "una-clave-nueva-bien-larga";

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final FakePasswordHasher hasher = spy(new FakePasswordHasher());
  private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
  private final InMemoryPasswordHistory history = new InMemoryPasswordHistory();
  private final InMemoryResetGrants grants = new InMemoryResetGrants();
  private final InMemoryRefreshTokenPort tokens = new InMemoryRefreshTokenPort();
  private final LoginAccountPort lookup = mock(LoginAccountPort.class);
  private final MembershipPort memberships = mock(MembershipPort.class);
  private final ResetCode code = new ResetCode("ABCDEFGHJKMN");

  private final ResetPasswordCommandHandler handler =
      new ResetPasswordCommandHandler(
          new ResetPasswordPorts(lookup, grants, memberships, hasher),
          new PasswordRenewal(
              new PasswordPolicy(Set.of("contraseña1234")), hasher, history, accounts, 5, clock),
          new SessionInvalidator(accounts, tokens, clock),
          new DirectUnitOfWork(),
          clock);

  private LoginAccount account(String status, boolean mustChange) {
    LoginAccount account =
        new LoginAccount(
            USER,
            "test.operator",
            "Persona de Prueba Uno",
            FakePasswordHasher.PREFIX + "unusable",
            status,
            null,
            0,
            0,
            mustChange);
    accounts.add(account);
    when(lookup.findByUsername("test.operator")).thenReturn(Optional.of(account));
    when(memberships.findActive(eq(USER), any()))
        .thenReturn(List.of(new Membership(AQUEDUCT, "OPERATOR", NOW.minusSeconds(60), null)));
    return account;
  }

  private void givenGrant(Instant expiresAt) {
    grants.save(USER, code.hash(), UUID.randomUUID(), expiresAt);
  }

  private ResetPasswordCommand reset(String username, String typedCode, String password) {
    return new ResetPasswordCommand(username, typedCode, password);
  }

  @Test
  void setsThePasswordUsesTheCodeAndEndsTheSessions() {
    account("ACTIVE", true);
    givenGrant(NOW.plus(Duration.ofHours(1)));
    new RefreshTokenService(tokens, new RecordingSecurityEvents(), new DirectUnitOfWork(), clock)
        .issueNewFamily(USER, Duration.ofDays(7), new ClientContext("ip", "JUnit"));

    PasswordResetResult result =
        handler.handle(reset("Test.Operator", "abcd-efgh-jkmn", NEW_PASSWORD));

    assertThat(result).isEqualTo(new PasswordResetResult(USER, AQUEDUCT));
    assertThat(accounts.accounts.get(USER).passwordHash())
        .isEqualTo(FakePasswordHasher.PREFIX + NEW_PASSWORD);
    assertThat(accounts.mustChange).containsEntry(USER, true);
    assertThat(grants.grants.getFirst().usedAt).isEqualTo(NOW);
    assertThat(accounts.tokenVersions).containsEntry(USER, 1);
    assertThat(tokens.tokens.getFirst().revokedReason())
        .isEqualTo(RevocationReason.PASSWORD_CHANGED);
  }

  @Test
  void theCodeWorksOnlyOnce() {
    account("ACTIVE", true);
    givenGrant(NOW.plus(Duration.ofHours(1)));
    handler.handle(reset("test.operator", code.display(), NEW_PASSWORD));

    assertThatThrownBy(
            () ->
                handler.handle(reset("test.operator", code.display(), "otra-clave-bastante-larga")))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void everyKindOfBadCodeIsTheSameUnauthorizedAndSpendsADecoyVerification() {
    account("ACTIVE", true);
    givenGrant(NOW.minusSeconds(1));
    when(lookup.findByUsername("ghost.user")).thenReturn(Optional.empty());

    List<ResetPasswordCommand> failures =
        List.of(
            reset("test.operator", code.display(), NEW_PASSWORD),
            reset("test.operator", "ZZZZ-ZZZZ-ZZZZ", NEW_PASSWORD),
            reset("test.operator", "not a code", NEW_PASSWORD),
            reset("ghost.user", code.display(), NEW_PASSWORD),
            reset("Not A Username!", code.display(), NEW_PASSWORD));
    for (ResetPasswordCommand failure : failures) {
      assertThatThrownBy(() -> handler.handle(failure)).isInstanceOf(UnauthorizedException.class);
    }

    verify(hasher, org.mockito.Mockito.times(failures.size())).verifyAgainstDecoy(any());
    assertThat(accounts.tokenVersions).isEmpty();
    assertThat(grants.grants.getFirst().usedAt).isNull();
  }

  @Test
  void aDisabledAccountCannotUseACode() {
    account("DISABLED", true);
    givenGrant(NOW.plus(Duration.ofHours(1)));

    assertThatThrownBy(() -> handler.handle(reset("test.operator", code.display(), NEW_PASSWORD)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void aCodeOfAnotherAccountDoesNotWork() {
    account("ACTIVE", true);
    grants.save(UUID.randomUUID(), code.hash(), UUID.randomUUID(), NOW.plus(Duration.ofHours(1)));

    assertThatThrownBy(() -> handler.handle(reset("test.operator", code.display(), NEW_PASSWORD)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void aWeakPasswordIsRefusedAndTheCodeIsNotConsumed() {
    account("ACTIVE", true);
    givenGrant(NOW.plus(Duration.ofHours(1)));

    assertThatThrownBy(() -> handler.handle(reset("test.operator", code.display(), "corta-11-ca")))
        .isInstanceOf(PasswordPolicyViolationException.class);

    assertThat(grants.grants.getFirst().usedAt).isNull();
    assertThat(accounts.tokenVersions).isEmpty();
    handler.handle(reset("test.operator", code.display(), NEW_PASSWORD));
  }

  @Test
  void aConcurrentUseOfTheSameCodeLosesTheRace() {
    account("ACTIVE", true);
    givenGrant(NOW.plus(Duration.ofHours(1)));
    // Another request used the code between the check and the update.
    var racing = new PasswordResetGrantPortRace(grants);
    ResetPasswordCommandHandler racingHandler =
        new ResetPasswordCommandHandler(
            new ResetPasswordPorts(lookup, racing, memberships, hasher),
            new PasswordRenewal(new PasswordPolicy(Set.of()), hasher, history, accounts, 5, clock),
            new SessionInvalidator(accounts, tokens, clock),
            new DirectUnitOfWork(),
            clock);

    assertThatThrownBy(
            () -> racingHandler.handle(reset("test.operator", code.display(), NEW_PASSWORD)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void anAccountWithoutMembershipStillGetsItsPasswordAndAnEmptyAqueduct() {
    account("ACTIVE", false);
    givenGrant(NOW.plus(Duration.ofHours(1)));
    when(memberships.findActive(eq(USER), any())).thenReturn(List.of());

    PasswordResetResult result =
        handler.handle(reset("test.operator", code.display(), NEW_PASSWORD));

    assertThat(result.aqueductId()).isNull();
    assertThat(handler.commandType()).isEqualTo(ResetPasswordCommand.class);
    assertThat(reset("a", "b", "secret-text").toString()).doesNotContain("secret-text");
  }

  /** Reports the code as usable but refuses to consume it, like a lost race. */
  private static final class PasswordResetGrantPortRace
      implements co.caudal.application.port.out.PasswordResetGrantPort {

    private final InMemoryResetGrants real;

    PasswordResetGrantPortRace(InMemoryResetGrants real) {
      this.real = real;
    }

    @Override
    public UUID save(UUID userId, String codeHash, UUID issuedBy, Instant expiresAt) {
      return real.save(userId, codeHash, issuedBy, expiresAt);
    }

    @Override
    public Optional<UUID> findUsable(UUID userId, String codeHash, Instant now) {
      return real.findUsable(userId, codeHash, now);
    }

    @Override
    public boolean markUsed(UUID grantId, Instant now) {
      return false;
    }

    @Override
    public int revokeOpen(UUID userId, Instant now) {
      return real.revokeOpen(userId, now);
    }
  }
}
