package co.caudal.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** P18 State: every legal transition, every illegal one, and the state read from the columns. */
class RefreshTokenStateTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");

  private final RefreshTokenState active = RefreshTokenState.of(null, null);
  private final RefreshTokenState rotated = RefreshTokenState.of(NOW, RevocationReason.ROTATED);
  private final RefreshTokenState revoked = RefreshTokenState.of(NOW, RevocationReason.LOGOUT);

  @Test
  void stateIsReadFromTheColumns() {
    assertThat(active.code()).isEqualTo("ACTIVE");
    assertThat(rotated.code()).isEqualTo("ROTATED");
    assertThat(revoked.code()).isEqualTo("REVOKED");
  }

  @Test
  void anActiveTokenRotatesIntoTheRotatedState() {
    assertThat(active.rotate().code()).isEqualTo("ROTATED");
    assertThatCode(active::ensureRotatable).doesNotThrowAnyException();
  }

  @Test
  void anActiveTokenCanBeRevokedForAnyReason() {
    for (RevocationReason reason : RevocationReason.values()) {
      String expected = reason == RevocationReason.ROTATED ? "ROTATED" : "REVOKED";
      assertThat(active.revoke(reason).code()).isEqualTo(expected);
    }
  }

  @Test
  void aRotatedTokenCannotRotateAgainAndThatIsAReuse() {
    assertThatThrownBy(rotated::rotate).isInstanceOf(RefreshTokenReuseException.class);
    assertThatThrownBy(rotated::ensureRotatable).isInstanceOf(RefreshTokenReuseException.class);
  }

  @Test
  void aRevokedTokenCannotRotateAndThatIsNotAReuse() {
    assertThatThrownBy(revoked::rotate)
        .isInstanceOf(SessionRevokedException.class)
        .isNotInstanceOf(RefreshTokenReuseException.class);
    assertThatThrownBy(revoked::ensureRotatable).isInstanceOf(SessionRevokedException.class);
  }

  @Test
  void revokingATokenThatIsAlreadyOutOfUseChangesNothing() {
    assertThat(rotated.revoke(RevocationReason.LOGOUT)).isSameAs(rotated);
    assertThat(revoked.revoke(RevocationReason.ADMIN)).isSameAs(revoked);
  }

  @Test
  void theTokenExposesItsStateAndChecksExpiryAfterTheState() {
    RefreshToken token =
        RefreshToken.issue(
            UUID.randomUUID(), UUID.randomUUID(), OpaqueToken.generate(), NOW, Duration.ofDays(7));

    assertThat(token.state().code()).isEqualTo("ACTIVE");
    assertThatCode(() -> token.ensureRotatable(NOW)).doesNotThrowAnyException();
    assertThatThrownBy(() -> token.ensureRotatable(NOW.plus(Duration.ofDays(7))))
        .isInstanceOf(UnauthorizedException.class);
  }
}
