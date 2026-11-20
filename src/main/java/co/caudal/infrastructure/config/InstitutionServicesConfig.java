package co.caudal.infrastructure.config;

import co.caudal.application.institution.PublicInstitutionDirectory;
import co.caudal.application.port.out.InstitutionDirectoryPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.infrastructure.institution.PublicInstitutionDirectoryProxy;
import co.caudal.infrastructure.ratelimit.RateLimitProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the public institution directory (proxy over the persistence subject). */
@Configuration(proxyBeanMethods = false)
class InstitutionServicesConfig {

  @Bean
  PublicInstitutionDirectory publicInstitutionDirectory(
      InstitutionDirectoryPort subject,
      RateLimiterPort rateLimiter,
      PrivacyHasherPort hasher,
      RateLimitProperties rates) {
    return new PublicInstitutionDirectoryProxy(
        subject,
        rateLimiter,
        hasher,
        new RateLimit(rates.publicDirectoryPerMinute(), Duration.ofMinutes(1)));
  }
}
