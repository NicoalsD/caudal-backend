package co.caudal.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RefreshTokenReuseException;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshReuseDetectionTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final ClientContext CLIENT = new ClientContext("ip-hmac-value", "JUnit");

  private final InMemoryRefreshTokenPort port = new InMemoryRefreshTokenPort();
  private final RecordingSecurityEvents events = new RecordingSecurityEvents();
  private final UnitOfWorkPort direct = new DirectUnitOfWork();
  private final RefreshTokenService service =
      new RefreshTokenService(port, events, direct, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void reusingARotatedTokenRevokesTheWholeFamily() {
    IssuedRefreshToken first = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    RotatedRefreshToken second = service.rotate(first.rawValue(), CLIENT);
    RotatedRefreshToken third = service.rotate(second.next().rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(RefreshTokenReuseException.class);

    assertThat(port.tokens).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
    assertThat(port.tokens.getLast().revokedReason()).isEqualTo(RevocationReason.REUSE_DETECTED);
    assertThatThrownBy(() -> service.rotate(third.next().rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);
  }

  @Test
  void reuseAnswersSessionRevoked() {
    IssuedRefreshToken first = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    service.rotate(first.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOfSatisfying(
            SessionRevokedException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_REVOKED));
  }

  @Test
  void reuseRecordsATokenReuseDetectedEventWithHighSeverity() {
    IssuedRefreshToken first = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    service.rotate(first.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);

    assertThat(events.recorded).hasSize(1);
    SecurityEvent event = events.recorded.getFirst();
    assertThat(event.type()).isEqualTo(SecurityEventType.TOKEN_REUSE_DETECTED);
    assertThat(event.severity()).isEqualTo(Severity.HIGH);
    assertThat(event.actorUserId()).isEqualTo(USER);
    assertThat(event.ipHmac()).isEqualTo("ip-hmac-value");
    assertThat(event.details())
        .containsEntry("family_id", port.tokens.getFirst().familyId().toString());
    assertThat(event.details().toString()).doesNotContain(first.rawValue());
  }

  @Test
  void aTokenRevokedByLogoutIsRejectedWithoutAReuseEvent() {
    IssuedRefreshToken first = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    port.revoke(0, RevocationReason.LOGOUT, NOW);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class)
        .isNotInstanceOf(RefreshTokenReuseException.class);
    assertThat(events.recorded).isEmpty();
  }

  @Test
  void otherFamiliesOfTheSameAccountAreNotTouched() {
    IssuedRefreshToken phoneOne = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    IssuedRefreshToken phoneTwo = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);
    service.rotate(phoneOne.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(phoneOne.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);

    assertThat(service.rotate(phoneTwo.rawValue(), CLIENT).userId()).isEqualTo(USER);
  }

  @Test
  void normalRotationRecordsNoEvent() {
    IssuedRefreshToken first = service.issueNewFamily(USER, Duration.ofDays(7), CLIENT);

    service.rotate(first.rawValue(), CLIENT);

    assertThat(events.recorded).isEmpty();
    assertThat(port.tokens).extracting(RefreshToken::isRevoked).containsExactly(true, false);
  }
}
