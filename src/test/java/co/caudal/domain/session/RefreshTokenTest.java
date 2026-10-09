package co.caudal.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID FAMILY = UUID.randomUUID();

  private final OpaqueToken secret = OpaqueToken.generate();
  private final RefreshToken token =
      RefreshToken.issue(USER, FAMILY, secret, NOW, Duration.ofDays(7));

  @Test
  void keepsOnlyTheHashOfTheSecret() {
    assertThat(token.tokenHash()).isEqualTo(secret.hash());
    assertThat(token.toString()).doesNotContain(secret.raw()).doesNotContain(secret.hash());
  }

  @Test
  void isActiveWhenIssued() {
    assertThat(token.id()).isNull();
    assertThat(token.isRevoked()).isFalse();
    assertThat(token.isExpired(NOW)).isFalse();
  }

  @Test
  void expiresExactlyAtTheEndOfItsLifetime() {
    assertThat(token.lifetime()).isEqualTo(Duration.ofDays(7));
    assertThat(token.isExpired(NOW.plus(Duration.ofDays(7)).minusSeconds(1))).isFalse();
    assertThat(token.isExpired(NOW.plus(Duration.ofDays(7)))).isTrue();
  }

  @Test
  void aRevokedTokenIsReportedAsRevoked() {
    RefreshToken revoked =
        new RefreshToken(
            UUID.randomUUID(),
            USER,
            FAMILY,
            token.tokenHash(),
            NOW,
            token.expiresAt(),
            NOW,
            RevocationReason.ROTATED,
            UUID.randomUUID());

    assertThat(revoked.isRevoked()).isTrue();
  }
}
