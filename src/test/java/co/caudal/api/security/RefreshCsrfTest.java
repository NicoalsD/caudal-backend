package co.caudal.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshCsrfTest {

  private static final String ALLOWED = "https://app.example.test";

  private final RefreshRequestGuard guard =
      new RefreshRequestGuard(new CorsProperties(List.of(ALLOWED, "http://localhost:5173")));

  private static MockHttpServletRequest request(String marker, String origin) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
    if (marker != null) {
      request.addHeader("X-Requested-With", marker);
    }
    if (origin != null) {
      request.addHeader("Origin", origin);
    }
    return request;
  }

  private void assertForbidden(MockHttpServletRequest request) {
    assertThatThrownBy(() -> guard.verify(request))
        .isInstanceOfSatisfying(
            DomainException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  void acceptsTheWebAppFromAnAllowedOrigin() {
    assertThatCode(() -> guard.verify(request("caudal-web", ALLOWED))).doesNotThrowAnyException();
    assertThatCode(() -> guard.verify(request("caudal-web", "http://localhost:5173")))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesARequestWithoutTheClientHeader() {
    assertForbidden(request(null, ALLOWED));
  }

  @Test
  void refusesAnotherValueOfTheClientHeader() {
    assertForbidden(request("XMLHttpRequest", ALLOWED));
    assertForbidden(request("Caudal-Web", ALLOWED));
  }

  @Test
  void refusesAForeignOrigin() {
    assertForbidden(request("caudal-web", "https://evil.example.test"));
  }

  @Test
  void refusesAnOriginThatOnlyLooksLikeAnAllowedOne() {
    assertForbidden(request("caudal-web", ALLOWED + ".evil.test"));
    assertForbidden(request("caudal-web", ALLOWED + "/"));
    assertForbidden(request("caudal-web", "http://app.example.test"));
  }

  @Test
  void refusesARequestWithoutOrigin() {
    assertForbidden(request("caudal-web", null));
  }

  @Test
  void aWildcardOriginIsNotAcceptableInTheConfiguration() {
    assertThatThrownBy(() -> new CorsProperties(List.of("*")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.test")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anEmptyOriginListIsNotAcceptableInTheConfiguration() {
    assertThatThrownBy(() -> new CorsProperties(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CorsProperties(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CorsProperties(List.of(" ")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
