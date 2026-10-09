package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.support.AppRoleIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** The real decoder, with the real database: raising the version kills older access tokens. */
class TokenVersionIT extends AppRoleIT {

  @Autowired private JwtDecoder decoder;

  private String tokenFor(UUID user, int version) {
    return TestJwts.sign(
        TestJwts.validClaims(user, "OPERATOR", UUID.randomUUID(), version, Instant.now()).build(),
        TestJwts.SECRET);
  }

  @Test
  void aPasswordChangeInvalidatesTheExistingToken() {
    UUID user = newUser("token.version", UNUSABLE_HASH);
    String token = tokenFor(user, 0);
    assertThat(decoder.decode(token).getSubject()).isEqualTo(user.toString());

    owner.update("UPDATE iam.users SET token_version = token_version + 1 WHERE id = ?", user);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void deactivatingTheAccountInvalidatesTheToken() {
    UUID user = newUser("token.disabled", UNUSABLE_HASH);
    String token = tokenFor(user, 0);

    owner.update(
        "UPDATE iam.users SET status = 'DISABLED', disabled_at = now() WHERE id = ?", user);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void aTokenForAnUnknownAccountIsRejected() {
    assertThatThrownBy(() -> decoder.decode(tokenFor(UUID.randomUUID(), 0)))
        .isInstanceOf(JwtException.class);
  }
}
