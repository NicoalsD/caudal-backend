package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.command.Done;
import co.caudal.domain.session.RevocationReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogoutAllCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final ClientContext CLIENT = new ClientContext("ip-hmac", "JUnit");

  private final InMemoryRefreshTokenPort port = new InMemoryRefreshTokenPort();
  private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RefreshTokenService tokens =
      new RefreshTokenService(port, new RecordingSecurityEvents(), new DirectUnitOfWork(), clock);
  private final LogoutAllCommandHandler handler =
      new LogoutAllCommandHandler(
          new SessionInvalidator(accounts, port, clock), new DirectUnitOfWork());

  @Test
  void revokesEveryFamilyOfTheAccountAndRaisesTheTokenVersion() {
    tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    Done done =
        handler.handle(
            new LogoutAllCommand(new Caller(USER, UUID.randomUUID(), "OPERATOR", "127.0.0.1")));

    assertThat(done).isEqualTo(Done.DONE);
    assertThat(port.tokens)
        .allMatch(
            token -> token.isRevoked() && token.revokedReason() == RevocationReason.LOGOUT_ALL);
    assertThat(accounts.tokenVersions).containsEntry(USER, 1);
  }

  @Test
  void doesNotTouchTheSessionsOfOtherAccounts() {
    UUID other = UUID.randomUUID();
    tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    tokens.issueNewFamily(other, Duration.ofDays(7), CLIENT);

    handler.handle(
        new LogoutAllCommand(new Caller(USER, UUID.randomUUID(), "OPERATOR", "127.0.0.1")));

    assertThat(port.tokens.get(0).isRevoked()).isTrue();
    assertThat(port.tokens.get(1).isRevoked()).isFalse();
    assertThat(accounts.tokenVersions).doesNotContainKey(other);
    assertThat(handler.commandType()).isEqualTo(LogoutAllCommand.class);
  }
}
