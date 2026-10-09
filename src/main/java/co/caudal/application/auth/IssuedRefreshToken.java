package co.caudal.application.auth;

import java.time.Instant;

/**
 * A refresh token as handed to the client: the only moment the raw secret exists outside the
 * client.
 *
 * @param rawValue the secret for the cookie
 * @param expiresAt when the session ends
 */
public record IssuedRefreshToken(String rawValue, Instant expiresAt) {

  @Override
  public String toString() {
    return "IssuedRefreshToken[expiresAt=" + expiresAt + "]";
  }
}
