package co.caudal.application.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * A refresh token as handed to the client: the only moment the raw secret exists outside the
 * client.
 *
 * @param rawValue the secret for the cookie
 * @param expiresAt when the session ends
 * @param timeToLive length of the session, for the cookie lifetime
 */
public record IssuedRefreshToken(String rawValue, Instant expiresAt, Duration timeToLive) {

  @Override
  public String toString() {
    return "IssuedRefreshToken[expiresAt=" + expiresAt + "]";
  }
}
