package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.auth.ClientContext;
import co.caudal.application.auth.IssuedRefreshToken;
import co.caudal.application.auth.RefreshTokenService;
import co.caudal.application.auth.RotatedRefreshToken;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.support.AppRoleIT;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Rotation against the real database, as the least-privilege application role. */
class RefreshRotationIT extends AppRoleIT {

  private static final ClientContext CLIENT = new ClientContext("a".repeat(64), "JUnit agent");

  @Autowired private RefreshTokenService service;

  private Map<String, Object> rowOf(String raw) {
    return owner.queryForMap(
        "SELECT * FROM iam.refresh_tokens WHERE token_hash = ?", OpaqueToken.hashOf(raw));
  }

  @Test
  void theDatabaseNeverContainsTheSecretOnlyItsHash() {
    UUID user = newUser("rotation.hash", UNUSABLE_HASH);

    IssuedRefreshToken issued = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);

    Integer rowsWithSecret =
        owner.queryForObject(
            "SELECT count(*) FROM iam.refresh_tokens WHERE token_hash = ?",
            Integer.class,
            issued.rawValue());
    assertThat(rowsWithSecret).isZero();
    Map<String, Object> row = rowOf(issued.rawValue());
    assertThat(row.get("token_hash")).isEqualTo(OpaqueToken.hashOf(issued.rawValue()));
    assertThat(row.get("created_ip_hmac")).isEqualTo(CLIENT.ipHmac());
    assertThat(row.get("user_agent")).isEqualTo("JUnit agent");
    assertThat(row.get("revoked_at")).isNull();
  }

  @Test
  void theNewTokenWorksAndThePreviousOneDoesNot() {
    UUID user = newUser("rotation.flow", UNUSABLE_HASH);
    IssuedRefreshToken first = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);

    RotatedRefreshToken rotated = service.rotate(first.rawValue(), CLIENT);

    assertThat(rotated.userId()).isEqualTo(user);
    assertThatThrownBy(() -> service.rotate(first.rawValue(), CLIENT))
        .isInstanceOf(SessionRevokedException.class);
  }

  @Test
  void theSuccessorOfARotationKeepsWorkingUntilAReuseHappens() {
    UUID user = newUser("rotation.chain", UNUSABLE_HASH);
    IssuedRefreshToken first = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    RotatedRefreshToken rotated = service.rotate(first.rawValue(), CLIENT);

    assertThat(service.rotate(rotated.next().rawValue(), CLIENT).userId()).isEqualTo(user);
  }

  @Test
  void rotationRecordsTheSuccessorAndKeepsTheFamily() {
    UUID user = newUser("rotation.family", UNUSABLE_HASH);
    IssuedRefreshToken first = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);

    RotatedRefreshToken rotated = service.rotate(first.rawValue(), CLIENT);

    Map<String, Object> old = rowOf(first.rawValue());
    Map<String, Object> next = rowOf(rotated.next().rawValue());
    assertThat(old.get("revoked_reason")).isEqualTo("ROTATED");
    assertThat(old.get("revoked_at")).isNotNull();
    assertThat(old.get("last_used_at")).isNotNull();
    assertThat(old.get("replaced_by_id")).isEqualTo(next.get("id"));
    assertThat(next.get("family_id")).isEqualTo(old.get("family_id"));
    assertThat(next.get("revoked_at")).isNull();
  }

  @Test
  void sessionsOfTheSameAccountHaveDifferentFamilies() {
    UUID user = newUser("rotation.two", UNUSABLE_HASH);

    IssuedRefreshToken one = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);
    IssuedRefreshToken two = service.issueNewFamily(user, Duration.ofDays(7), CLIENT);

    List<Object> families =
        List.of(rowOf(one.rawValue()).get("family_id"), rowOf(two.rawValue()).get("family_id"));
    assertThat(families.get(0)).isNotEqualTo(families.get(1));
  }

  @Test
  void anUnknownSecretIsRejected() {
    assertThatThrownBy(() -> service.rotate(OpaqueToken.generate().raw(), CLIENT))
        .isInstanceOf(UnauthorizedException.class);
  }
}
