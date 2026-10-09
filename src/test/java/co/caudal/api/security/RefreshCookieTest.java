package co.caudal.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshCookieTest {

  private static ResponseCookie cookie(String sameSite, boolean secure) {
    return new RefreshCookieFactory(new RefreshCookieProperties(sameSite, secure))
        .create("opaque-secret", Duration.ofDays(7));
  }

  @Test
  void carriesTheSecurityAttributes() {
    ResponseCookie cookie = cookie("None", true);

    assertThat(cookie.getName()).isEqualTo("caudal_rt");
    assertThat(cookie.getValue()).isEqualTo("opaque-secret");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cookie.getSameSite()).isEqualTo("None");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void headerValueHasEveryAttribute() {
    assertThat(cookie("None", true).toString())
        .contains("caudal_rt=opaque-secret")
        .contains("Path=/api/v1/auth")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=None")
        .contains("Max-Age=604800");
  }

  @Test
  void sameSiteFollowsTheConfiguration() {
    assertThat(cookie("Strict", true).getSameSite()).isEqualTo("Strict");
    assertThat(cookie("lax", false).getSameSite()).isEqualTo("Lax");
    assertThat(cookie("STRICT", true).getSameSite()).isEqualTo("Strict");
  }

  @Test
  void clearingExpiresTheCookieWithTheSameScope() {
    ResponseCookie cleared =
        new RefreshCookieFactory(new RefreshCookieProperties("None", true)).clear();

    assertThat(cleared.getValue()).isEmpty();
    assertThat(cleared.getMaxAge()).isZero();
    assertThat(cleared.getPath()).isEqualTo("/api/v1/auth");
    assertThat(cleared.isHttpOnly()).isTrue();
    assertThat(cleared.isSecure()).isTrue();
  }

  @Test
  void sameSiteNoneWithoutSecureIsRefused() {
    assertThatThrownBy(() -> new RefreshCookieProperties("None", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknownOrMissingSameSiteIsRefused() {
    assertThatThrownBy(() -> new RefreshCookieProperties("Whatever", true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RefreshCookieProperties(null, true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RefreshCookieProperties(" ", true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
