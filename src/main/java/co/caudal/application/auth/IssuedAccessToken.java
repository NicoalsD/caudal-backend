package co.caudal.application.auth;

import java.time.Duration;

/**
 * A signed access token and how long it lives.
 *
 * @param value the compact JWT
 * @param timeToLive lifetime from the moment of issue
 */
public record IssuedAccessToken(String value, Duration timeToLive) {

  @Override
  public String toString() {
    return "IssuedAccessToken[timeToLive=" + timeToLive + "]";
  }
}
