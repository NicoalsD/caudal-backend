package co.caudal.infrastructure.security;

import co.caudal.api.security.CorsProperties;
import co.caudal.api.security.RefreshRequestGuard;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * HTTP security of the API (docs/Seguridad.md, sections 2.3, 2.5, 2.8 and 2.9).
 *
 * <ul>
 *   <li>Stateless: no session, no CSRF cookie. The access token goes in {@code Authorization}.
 *   <li>Deny by default: only the listed paths are public; everything else needs a valid token, and
 *       each endpoint adds its permission with {@code @PreAuthorize}.
 *   <li>401 and 403 use the canonical error body.
 *   <li>CORS only for the configured origins, with credentials, never {@code *}.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(PermissionProperties.class)
public class SecurityConfig {

  /** Paths that anyone can call. Everything else is authenticated. */
  static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/public/**",
    "/actuator/health",
    "/actuator/health/**",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/swagger-ui.html",
    "/swagger-ui/**"
  };

  private static final String[] DOCS_PATHS = {"/swagger-ui.html", "/swagger-ui/**"};

  private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
  private static final String DOCS_CSP =
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'"
          + " data:; frame-ancestors 'none'";
  private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";
  private static final String CSP_HEADER = "Content-Security-Policy";

  @Bean
  JwtPermissionsConverter jwtPermissionsConverter(PermissionCatalog catalog) {
    return new JwtPermissionsConverter(catalog);
  }

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      JwtPermissionsConverter permissions,
      ApiAuthenticationEntryPoint entryPoint,
      ApiAccessDeniedHandler deniedHandler,
      SecurityErrorWriter errors,
      RefreshRequestGuard refreshGuard)
      throws Exception {
    RequestMatcher docs = docsMatcher();
    return http.csrf(
            csrf -> csrf.requireCsrfProtectionMatcher(cookieDependentRequests(refreshGuard)))
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(
            headers ->
                headers
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .addHeaderWriter(
                        new DelegatingRequestMatcherHeaderWriter(
                            new NegatedRequestMatcher(docs),
                            new StaticHeadersWriter(CSP_HEADER, API_CSP)))
                    .addHeaderWriter(
                        new DelegatingRequestMatcherHeaderWriter(
                            docs, new StaticHeadersWriter(CSP_HEADER, DOCS_CSP)))
                    .addHeaderWriter(
                        new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(permissions))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .addFilterBefore(
            new AuthorizationHeaderSizeFilter(errors), BearerTokenAuthenticationFilter.class)
        .build();
  }

  /**
   * CSRF protection for the only requests that depend on a cookie: the ones to the session
   * endpoints (refresh and logout, where the {@code caudal_rt} cookie is the credential). They need
   * the {@code X-Requested-With: caudal-web} header and an allowed {@code Origin}, a custom header
   * that a cross-site form or image cannot add (OWASP: custom request headers). A request that does
   * not have them is refused by the CSRF filter with {@code 403 FORBIDDEN} in the canonical format.
   * Requests that carry {@code Authorization: Bearer} do not depend on cookies, so they need no
   * token; the rest of the API reads no cookie.
   */
  static RequestMatcher cookieDependentRequests(RefreshRequestGuard guard) {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    RequestMatcher sessionEndpoints =
        request ->
            paths.matcher("/api/v1/auth/refresh").matches(request)
                || paths.matcher("/api/v1/auth/logout").matches(request);
    return request -> {
      String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
      boolean bearer = authorization != null && authorization.startsWith("Bearer ");
      return sessionEndpoints.matches(request) && !bearer && !guard.isValid(request);
    };
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(cors.allowedOrigins());
    configuration.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            "X-Requested-With",
            "X-Request-Id",
            "Idempotency-Key"));
    configuration.setExposedHeaders(List.of("X-Request-Id", HttpHeaders.RETRY_AFTER));
    configuration.setAllowCredentials(true);
    // A foreign origin gets no CORS configuration: no Access-Control-* headers, and the request
    // goes on to the security rules, which answer in the canonical format.
    return request ->
        cors.allowedOrigins().contains(request.getHeader(HttpHeaders.ORIGIN))
            ? configuration
            : null;
  }

  private static RequestMatcher docsMatcher() {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    return request -> {
      for (String pattern : DOCS_PATHS) {
        if (paths.matcher(pattern).matches(request)) {
          return true;
        }
      }
      return false;
    };
  }
}
