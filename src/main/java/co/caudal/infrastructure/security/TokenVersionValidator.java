package co.caudal.infrastructure.security;

import co.caudal.application.port.out.TokenVersionPort;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Checks the session claims of the token against the account.
 *
 * <p>The token must carry a user id in {@code sub} and a numeric {@code tv}, and {@code tv} must
 * equal {@code iam.users.token_version}. A change of password, role or status raises the version,
 * so every older token stops working at once (docs/Seguridad.md, section 2.3). The lookup runs on
 * each request: it is a primary key read.
 */
final class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_SESSION =
      new OAuth2Error("invalid_token", "The token does not belong to a current session", null);

  private final TokenVersionPort versions;

  TokenVersionValidator(TokenVersionPort versions) {
    this.versions = versions;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    UUID userId = parseUserId(token.getSubject());
    Object tokenVersion = token.getClaim(JwtAccessTokenIssuerAdapter.CLAIM_TOKEN_VERSION);
    if (userId == null || !(tokenVersion instanceof Number number)) {
      return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }
    OptionalInt current = versions.findCurrentVersion(userId);
    return current.isPresent() && current.getAsInt() == number.intValue()
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
  }

  private static UUID parseUserId(String subject) {
    if (subject == null) {
      return null;
    }
    try {
      return UUID.fromString(subject);
    } catch (IllegalArgumentException notAUuid) {
      return null;
    }
  }
}
