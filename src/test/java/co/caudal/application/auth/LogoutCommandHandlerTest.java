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

class LogoutCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final ClientContext CLIENT = new ClientContext("ip-hmac", "JUnit");

  private final InMemoryRefreshTokenPort port = new InMemoryRefreshTokenPort();
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final RefreshTokenService tokens =
      new RefreshTokenService(port, new RecordingSecurityEvents(), new DirectUnitOfWork(), clock);
  private final LogoutCommandHandler handler =
      new LogoutCommandHandler(port, new DirectUnitOfWork(), clock);

  private static Caller caller(UUID user) {
    return new Caller(user, UUID.randomUUID(), "OPERATOR", "127.0.0.1");
  }

  @Test
  void revokesTheWholeFamilyOfTheCookieToken() {
    IssuedRefreshToken first = tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    RotatedRefreshToken second = tokens.rotate(first.rawValue(), CLIENT);

    Done done = handler.handle(new LogoutCommand(caller(USER), second.next().rawValue()));

    assertThat(done).isEqualTo(Done.DONE);
    assertThat(port.tokens).allMatch(token -> token.isRevoked());
    assertThat(port.tokens.get(1).revokedReason()).isEqualTo(RevocationReason.LOGOUT);
    assertThat(port.tokens.get(1).revokedAt()).isEqualTo(NOW);
  }

  @Test
  void leavesTheOtherSessionsOfTheAccountOpen() {
    IssuedRefreshToken phone = tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    handler.handle(new LogoutCommand(caller(USER), phone.rawValue()));

    assertThat(port.tokens.get(0).isRevoked()).isTrue();
    assertThat(port.tokens.get(1).isRevoked()).isFalse();
  }

  @Test
  void ignoresATokenThatBelongsToAnotherAccount() {
    IssuedRefreshToken foreign =
        tokens.issueNewFamily(UUID.randomUUID(), Duration.ofDays(7), CLIENT);

    handler.handle(new LogoutCommand(caller(USER), foreign.rawValue()));

    assertThat(port.tokens.getFirst().isRevoked()).isFalse();
  }

  @Test
  void aMissingUnknownOrAbsurdTokenIsNotAnError() {
    tokens.issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    assertThat(handler.handle(new LogoutCommand(caller(USER), null))).isEqualTo(Done.DONE);
    assertThat(handler.handle(new LogoutCommand(caller(USER), "unknown"))).isEqualTo(Done.DONE);
    assertThat(handler.handle(new LogoutCommand(caller(USER), "x".repeat(500))))
        .isEqualTo(Done.DONE);

    assertThat(port.tokens.getFirst().isRevoked()).isFalse();
  }

  @Test
  void theCommandNeverPrintsTheSecret() {
    assertThat(new LogoutCommand(caller(USER), "super-secret-value").toString())
        .doesNotContain("super-secret-value");
    assertThat(handler.commandType()).isEqualTo(LogoutCommand.class);
  }
}
