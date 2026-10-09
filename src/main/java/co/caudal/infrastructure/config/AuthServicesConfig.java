package co.caudal.infrastructure.config;

import co.caudal.application.auth.RefreshTokenService;
import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.infrastructure.security.RefreshProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases and services of the {@code application} layer, which has no Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshProperties.class)
class AuthServicesConfig {

  @Bean
  RefreshTokenService refreshTokenService(
      RefreshTokenPort tokens, SecurityEventPort events, UnitOfWorkPort unitOfWork, Clock clock) {
    return new RefreshTokenService(tokens, events, unitOfWork, clock);
  }
}
