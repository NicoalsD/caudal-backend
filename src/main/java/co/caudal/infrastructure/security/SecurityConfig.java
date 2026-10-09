package co.caudal.infrastructure.security;

import co.caudal.api.security.CorsProperties;
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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
      SecurityErrorWriter errors)
      throws Exception {
    RequestMatcher docs = docsMatcher();
    return http.csrf(AbstractHttpConfigurer::disable)
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
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
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
