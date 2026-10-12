package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.PasswordPolicy;
import co.caudal.domain.user.PasswordPolicyViolationException;
import co.caudal.domain.user.PasswordPolicyViolationException.Rule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangePasswordCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final int HISTORY_SIZE = 5;
  private static final String CURRENT = "clave-actual-larga-1";

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final FakePasswordHasher hasher = new FakePasswordHasher();
  private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
  private final InMemoryPasswordHistory history = new InMemoryPasswordHistory();
  private final InMemoryRefreshTokenPort tokens = new InMemoryRefreshTokenPort();
  private final DirectUnitOfWork unitOfWork = new DirectUnitOfWork();
  private final ChangePasswordCommandHandler handler =
      new ChangePasswordCommandHandler(
          accounts,
          hasher,
          new PasswordRenewal(
              new PasswordPolicy(Set.of("contraseña1234")),
              hasher,
              history,
              accounts,
              HISTORY_SIZE,
              clock),
          new SessionInvalidator(accounts, tokens, clock),
          unitOfWork);

  private void givenAccount(String status, boolean mustChange) {
    accounts.add(
        new LoginAccount(
            USER,
            "test.operator",
            "Persona de Prueba Uno",
            FakePasswordHasher.PREFIX + CURRENT,
            status,
            null,
            0,
            0,
            mustChange));
  }

  private ChangePasswordCommand change(String current, String next) {
    return new ChangePasswordCommand(
        new Caller(USER, UUID.randomUUID(), "OPERATOR", "127.0.0.1"), current, next);
  }

  @Test
  void changesThePasswordKeepsTheOldHashAndEndsEverySession() {
    givenAccount("ACTIVE", true);
    new RefreshTokenService(tokens, new RecordingSecurityEvents(), unitOfWork, clock)
        .issueNewFamily(USER, Duration.ofDays(7), new ClientContext("ip", "JUnit"));

    handler.handle(change(CURRENT, "otra-clave-bastante-larga"));

    assertThat(accounts.accounts.get(USER).passwordHash())
        .isEqualTo(FakePasswordHasher.PREFIX + "otra-clave-bastante-larga");
    assertThat(accounts.mustChange).containsEntry(USER, false);
    assertThat(history.entries)
        .extracting(InMemoryPasswordHistory.Entry::hash)
        .containsExactly(FakePasswordHasher.PREFIX + CURRENT);
    assertThat(accounts.tokenVersions).containsEntry(USER, 1);
    assertThat(tokens.tokens.getFirst().revokedReason())
        .isEqualTo(RevocationReason.PASSWORD_CHANGED);
  }

  @Test
  void aWrongCurrentPasswordIsUnauthorizedAndChangesNothing() {
    givenAccount("ACTIVE", false);

    assertThatThrownBy(
            () -> handler.handle(change("clave-que-no-es-larga", "otra-clave-bastante-larga")))
        .isInstanceOf(UnauthorizedException.class);

    assertThat(accounts.accounts.get(USER).passwordHash())
        .isEqualTo(FakePasswordHasher.PREFIX + CURRENT);
    assertThat(history.entries).isEmpty();
    assertThat(accounts.tokenVersions).isEmpty();
  }

  @Test
  void anUnknownOrDisabledAccountIsUnauthorized() {
    assertThatThrownBy(() -> handler.handle(change(CURRENT, "otra-clave-bastante-larga")))
        .isInstanceOf(UnauthorizedException.class);

    givenAccount("DISABLED", false);
    assertThatThrownBy(() -> handler.handle(change(CURRENT, "otra-clave-bastante-larga")))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void aPolicyViolationLeavesThePasswordAndTheSessionsAlone() {
    givenAccount("ACTIVE", false);

    assertThatThrownBy(() -> handler.handle(change(CURRENT, "corta-11-ca")))
        .isInstanceOfSatisfying(
            PasswordPolicyViolationException.class,
            e -> assertThat(e.rule()).isEqualTo(Rule.TOO_SHORT));
    assertThatThrownBy(() -> handler.handle(change(CURRENT, "contraseña1234")))
        .isInstanceOfSatisfying(
            PasswordPolicyViolationException.class,
            e -> assertThat(e.rule()).isEqualTo(Rule.COMMON));
    assertThatThrownBy(() -> handler.handle(change(CURRENT, "xx-test.operator-xx")))
        .isInstanceOfSatisfying(
            PasswordPolicyViolationException.class,
            e -> assertThat(e.rule()).isEqualTo(Rule.CONTAINS_USERNAME));

    assertThat(history.entries).isEmpty();
    assertThat(accounts.tokenVersions).isEmpty();
  }

  @Test
  void thePasswordInForceCannotBeChosenAgain() {
    givenAccount("ACTIVE", false);

    assertThatThrownBy(() -> handler.handle(change(CURRENT, CURRENT)))
        .isInstanceOfSatisfying(
            PasswordPolicyViolationException.class,
            e -> assertThat(e.rule()).isEqualTo(Rule.RECENTLY_USED));
  }

  @Test
  void theLastFivePasswordsCannotBeUsedAndTheSixthBackCan() {
    givenAccount("ACTIVE", false);
    String[] passwords = {
      CURRENT,
      "segunda-clave-larga-2",
      "tercera-clave-larga-3",
      "cuarta-clave-larga-4",
      "quinta-clave-larga-5",
      "sexta-clave-larga-6"
    };
    for (int i = 1; i < passwords.length; i++) {
      handler.handle(change(passwords[i - 1], passwords[i]));
    }

    // In force: the 6th. Remembered: 5th, 4th, 3rd, 2nd. The 1st is out of the window.
    for (int i = 1; i < passwords.length; i++) {
      String reused = passwords[i];
      assertThatThrownBy(() -> handler.handle(change(passwords[5], reused)))
          .isInstanceOf(PasswordPolicyViolationException.class);
    }
    handler.handle(change(passwords[5], passwords[0]));
    assertThat(accounts.accounts.get(USER).passwordHash())
        .isEqualTo(FakePasswordHasher.PREFIX + passwords[0]);
    assertThat(handler.commandType()).isEqualTo(ChangePasswordCommand.class);
  }

  @Test
  void theCommandNeverPrintsThePasswords() {
    assertThat(change("clave-actual-secreta", "clave-nueva-secreta").toString())
        .doesNotContain("secreta");
  }
}
