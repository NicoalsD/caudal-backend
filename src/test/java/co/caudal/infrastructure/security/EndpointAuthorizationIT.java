package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.caudal.support.SessionIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Enumerates every {@code /api/v1} route from the Spring MVC mapping, so a new endpoint is covered
 * without a test being written for it. Without a token each route answers 401; the routes that are
 * public by design ({@link SecurityConfig}) are skipped, and the device routes, which are public
 * for Spring Security but signed by the device, must answer 401 without a signature.
 */
class EndpointAuthorizationIT extends SessionIT {

  private static final String API = "/api/v1/";
  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final String PLACEHOLDER = "00000000-0000-4000-8000-000000000001";
  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  /** Device routes that are public for Spring Security and checked by the device signature. */
  private static final List<String> DEVICE_SIGNED =
      List.of(
          "/api/v1/devices/telemetry",
          "/api/v1/devices/commands",
          "/api/v1/devices/commands/{id}/ack");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mapping;

  /** One concrete request: the method, the mapping pattern and the URI with a placeholder id. */
  private record Route(String method, String pattern, String uri) {}

  private List<Route> apiRoutes() {
    Set<Route> routes = new LinkedHashSet<>();
    for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      Set<RequestMethod> effective = methods.isEmpty() ? EnumSet.of(RequestMethod.GET) : methods;
      for (String pattern : info.getPatternValues()) {
        if (!pattern.startsWith(API)) {
          continue;
        }
        String uri = pattern.replaceAll("\\{[^}]+}", PLACEHOLDER);
        for (RequestMethod method : effective) {
          routes.add(new Route(method.name(), pattern, uri));
        }
      }
    }
    return new ArrayList<>(routes);
  }

  private static boolean isPublic(String uri) {
    return Arrays.stream(SecurityConfig.PUBLIC_PATHS).anyMatch(p -> MATCHER.match(p, uri));
  }

  @Test
  void everyApiRouteAnswers401WithoutATokenOrASignature() throws Exception {
    List<Route> routes = apiRoutes();
    List<String> patterns = routes.stream().map(Route::pattern).distinct().toList();
    assertThat(patterns)
        .contains(
            "/api/v1/tanks/{id}/status",
            "/api/v1/readings/batch",
            "/api/v1/devices/{id}/keys",
            "/api/v1/valve-commands/manual",
            "/api/v1/audit-log",
            "/api/v1/security-events");
    assertThat(patterns).containsAll(DEVICE_SIGNED);

    List<String> failures = new ArrayList<>();
    int checked = 0;
    for (Route route : routes) {
      boolean device = DEVICE_SIGNED.contains(route.pattern());
      if (!device && isPublic(route.uri())) {
        continue;
      }
      checked++;
      MockHttpServletRequestBuilder builder =
          request(HttpMethod.valueOf(route.method()), route.uri())
              .contentType("application/json")
              .content("{}");
      if (LOGOUT.equals(route.uri())) {
        // The cookie-dependent session endpoints pass the CSRF headers, as the web app sends them.
        builder = builder.header("X-Requested-With", "caudal-web").header("Origin", ORIGIN);
      }
      int status = mvc.perform(builder).andReturn().getResponse().getStatus();
      if (status != 401) {
        failures.add(route.method() + " " + route.pattern() + " -> " + status);
      }
    }

    assertThat(failures).as("routes that did not answer 401 without credentials").isEmpty();
    assertThat(checked).as("routes that were actually checked").isGreaterThan(50);
  }

  /**
   * Logout reads the refresh cookie, so the CSRF filter of {@link SecurityConfig} answers 403 to a
   * request without the {@code X-Requested-With} and {@code Origin} headers, before authentication.
   * That is the documented behaviour (docs/Seguridad.md); with the headers, a missing token is 401.
   */
  @Test
  void logoutWithoutTheCsrfHeadersIsRefusedBeforeAuthentication() throws Exception {
    mvc.perform(post(LOGOUT).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    mvc.perform(
            post(LOGOUT)
                .header("X-Requested-With", "caudal-web")
                .header("Origin", ORIGIN)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void theDeviceRoutesAreExactlyTheSignedOnesAndArePublicForSpringSecurity() {
    for (String pattern : DEVICE_SIGNED) {
      String uri = pattern.replaceAll("\\{[^}]+}", PLACEHOLDER);
      assertThat(isPublic(uri)).as(pattern).isTrue();
    }
    assertThat(isPublic("/api/v1/devices/" + UUID.randomUUID() + "/keys")).isFalse();
  }
}
