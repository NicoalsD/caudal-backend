package co.caudal.api.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code caudal_rt} cookie that carries the refresh token (docs/Seguridad.md, section
 * 2.3): {@code HttpOnly} so scripts cannot read it, {@code Secure}, {@code SameSite} from the
 * configuration and a {@code Path} limited to the auth endpoints so the browser does not send it
 * anywhere else.
 */
@Component
public class RefreshCookieFactory {

  /** Name of the cookie. */
  public static final String COOKIE_NAME = "caudal_rt";

  /** Path the cookie is sent to. */
  public static final String COOKIE_PATH = "/api/v1/auth";

  private final RefreshCookieProperties properties;

  RefreshCookieFactory(RefreshCookieProperties properties) {
    this.properties = properties;
  }

  /**
   * Cookie that stores a refresh token.
   *
   * @param secret the raw refresh token
   * @param timeToLive how long the browser keeps it
   * @return the cookie
   */
  public ResponseCookie create(String secret, Duration timeToLive) {
    return base(secret).maxAge(timeToLive).build();
  }

  /**
   * Cookie that makes the browser forget the refresh token (logout).
   *
   * @return an expired cookie with the same attributes
   */
  public ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(properties.secure())
        .sameSite(properties.sameSite())
        .path(COOKIE_PATH);
  }
}
