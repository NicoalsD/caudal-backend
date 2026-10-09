package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.auth.ClientContext;
import co.caudal.application.auth.IssuedRefreshToken;
import co.caudal.application.auth.RefreshTokenService;
import co.caudal.application.auth.RotatedRefreshToken;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.support.AppRoleIT;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reuse detection against the real database, as the least-privilege application role. */
class RefreshReuseDetectionIT extends AppRoleIT {

  private static final ClientContext CLIENT = new ClientContext("b".repeat(64), "JUnit agent");

  @Autowired private RefreshTokenService service;

  private Map<String, Object> rowOf(String raw) {
    return owner.queryForMap(
        "SELECT * FROM iam.refresh_tokens WHERE token_hash = ?", OpaqueToken.hashOf(raw));
  }

  @Test
  void reuseRevokesTheFamilyAndKeepsTheRevocationDespiteTheError() {
    UUID user = newUser("reuse.family", UNUSABLE_HASH);
    IssuedRefreshToken first = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    RotatedRefreshToken second = service.rotate(first.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);

    Map<String, Object> newest = rowOf(second.next().rawValue());
    assertThat(newest.get("revoked_reason")).isEqualTo("REUSE_DETECTED");
    assertThat(newest.get("revoked_at")).isNotNull();
    assertThat(rowOf(first.rawValue()).get("revoked_reason")).isEqualTo("ROTATED");
    assertThatThrownBy(() -> service.rotate(second.next().rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);
  }

  @Test
  void reuseRecordsATokenReuseDetectedSecurityEvent() {
    UUID user = newUser("reuse.event", UNUSABLE_HASH);
    IssuedRefreshToken first = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    service.rotate(first.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);

    List<Map<String, Object>> events =
        owner.queryForList(
            "SELECT * FROM iam.security_events WHERE type = 'TOKEN_REUSE_DETECTED'"
                + " AND actor_user_id = ?",
            user);
    assertThat(events).hasSize(1);
    Map<String, Object> event = events.getFirst();
    assertThat(event.get("severity")).isEqualTo("HIGH");
    assertThat(event.get("ip_hmac")).isEqualTo(CLIENT.ipHmac());
    assertThat(event.get("details").toString())
        .contains("family_id")
        .doesNotContain(first.rawValue());
  }

  @Test
  void otherFamiliesOfTheAccountSurvive() {
    UUID user = newUser("reuse.other", UNUSABLE_HASH);
    IssuedRefreshToken phoneOne = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    IssuedRefreshToken phoneTwo = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    service.rotate(phoneOne.rawValue(), CLIENT);

    assertThatThrownBy(() -> service.rotate(phoneOne.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);

    assertThat(service.rotate(phoneTwo.rawValue(), CLIENT).userId()).isEqualTo(user);
  }
}
