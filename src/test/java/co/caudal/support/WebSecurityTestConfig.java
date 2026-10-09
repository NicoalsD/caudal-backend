package co.caudal.support;

import co.caudal.api.security.AuthWebConfig;
import co.caudal.application.port.out.PermissionPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.infrastructure.security.ApiAccessDeniedHandler;
import co.caudal.infrastructure.security.ApiAuthenticationEntryPoint;
import co.caudal.infrastructure.security.PermissionCatalog;
import co.caudal.infrastructure.security.SecurityConfig;
import co.caudal.infrastructure.security.SecurityErrorWriter;
import co.caudal.infrastructure.time.ClockConfig;
import java.util.Set;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Real security chain for {@code @WebMvcTest} slices: the same filter chain, error format and
 * deny-by-default rules as production, with the token decoder and the database replaced by mocks.
 * Tests authenticate with {@code SecurityMockMvcRequestPostProcessors.jwt()}.
 */
@TestConfiguration
@EnableWebSecurity
@Import({
  SecurityConfig.class,
  SecurityErrorWriter.class,
  ApiAuthenticationEntryPoint.class,
  ApiAccessDeniedHandler.class,
  PermissionCatalog.class,
  AuthWebConfig.class,
  ClockConfig.class
})
public class WebSecurityTestConfig {

  @Bean
  JwtDecoder jwtDecoder() {
    return Mockito.mock(JwtDecoder.class);
  }

  @Bean
  PermissionPort permissionPort() {
    PermissionPort port = Mockito.mock(PermissionPort.class);
    Mockito.when(port.findPermissionCodes(Mockito.anyString())).thenReturn(Set.of());
    return port;
  }

  @Bean
  SecurityEventPort securityEventPort() {
    return Mockito.mock(SecurityEventPort.class);
  }
}
