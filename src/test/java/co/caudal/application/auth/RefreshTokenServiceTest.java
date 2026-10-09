package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final ClientContext CLIENT = new ClientContext("ip-hmac", "JUnit");

  private final InMemoryRefreshTokenPort port = new InMemoryRefreshTokenPort();
  private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private RefreshTokenService service() {
    return new RefreshTokenService(port, Direct.INSTANCE, clock);
  }

  /** Transaction-less unit of work: runs the work directly. */
  private enum Direct implements co.caudal.application.port.out.UnitOfWorkPort {
    INSTANCE;

    @Override
    public <T> T execute(Supplier<T> work) {
      return work.get();
    }
  }

  @Test
  void issuesTheFirstTokenOfAFamilyAndStoresOnlyItsHash() {
    IssuedRefreshToken issued = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    RefreshToken stored = port.tokens.getFirst();
    assertThat(stored.tokenHash()).isEqualTo(OpaqueToken.hashOf(issued.rawValue()));
    assertThat(stored.tokenHash()).isNotEqualTo(issued.rawValue());
    assertThat(stored.userId()).isEqualTo(USER);
    assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(port.tokens).noneMatch(token -> token.toString().contains(issued.rawValue()));
  }

  @Test
  void rotationReturnsANewTokenInTheSameFamilyWithTheSameLifetime() {
    IssuedRefreshToken first = service().issueNewFamily(USER, Duration.ofDays(30), CLIENT);

    RotatedRefreshToken rotated = service().rotate(first.rawValue(), CLIENT);

    assertThat(rotated.userId()).isEqualTo(USER);
    assertThat(rotated.next().rawValue()).isNotEqualTo(first.rawValue());
    assertThat(rotated.next().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    assertThat(port.tokens).hasSize(2);
    assertThat(port.tokens.get(1).familyId()).isEqualTo(port.tokens.get(0).familyId());
  }

  @Test
  void rotationMarksThePresentedTokenAsRotatedAndLinksItsSuccessor() {
    IssuedRefreshToken first = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    service().rotate(first.rawValue(), CLIENT);

    RefreshToken old = port.tokens.get(0);
    assertThat(old.isRevoked()).isTrue();
    assertThat(old.revokedReason()).isEqualTo("ROTATED");
    assertThat(old.replacedById()).isEqualTo(port.tokens.get(1).id());
    assertThat(port.tokens.get(1).isRevoked()).isFalse();
  }

  @Test
  void theRotatedTokenKeepsWorking() {
    IssuedRefreshToken first = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    RotatedRefreshToken second = service().rotate(first.rawValue(), CLIENT);

    RotatedRefreshToken third = service().rotate(second.next().rawValue(), CLIENT);

    assertThat(third.userId()).isEqualTo(USER);
    assertThat(port.tokens).hasSize(3);
  }

  @Test
  void anAlreadyRotatedTokenIsRejected() {
    IssuedRefreshToken first = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    service().rotate(first.rawValue(), CLIENT);

    assertThatThrownBy(() -> service().rotate(first.rawValue(), CLIENT))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void anUnknownTokenIsRejected() {
    assertThatThrownBy(() -> service().rotate(OpaqueToken.generate().raw(), CLIENT))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void missingOrAbsurdValuesAreRejectedWithoutTouchingTheStore() {
    assertThatThrownBy(() -> service().rotate(null, CLIENT))
        .isInstanceOf(UnauthorizedException.class);
    assertThatThrownBy(() -> service().rotate("", CLIENT))
        .isInstanceOf(UnauthorizedException.class);
    assertThatThrownBy(() -> service().rotate("x".repeat(10_000), CLIENT))
        .isInstanceOf(UnauthorizedException.class);
    assertThat(port.tokens).isEmpty();
  }

  @Test
  void anExpiredTokenIsRejected() {
    IssuedRefreshToken first = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    clock = Clock.fixed(NOW.plus(Duration.ofDays(7)), ZoneOffset.UTC);

    assertThatThrownBy(() -> service().rotate(first.rawValue(), CLIENT))
        .isInstanceOf(UnauthorizedException.class);
    assertThat(port.tokens).hasSize(1);
  }

  @Test
  void toStringOfTheIssuedTokenDoesNotShowTheSecret() {
    IssuedRefreshToken issued = service().issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    assertThat(issued.toString()).doesNotContain(issued.rawValue());
  }

  @Test
  void userAgentIsTrimmedToTheColumnSize() {
    ClientContext client = new ClientContext("ip-hmac", "A".repeat(500));

    assertThat(client.userAgent()).hasSize(ClientContext.USER_AGENT_MAX);
  }
}
