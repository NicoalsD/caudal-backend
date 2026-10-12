package co.caudal.infrastructure.security;

import co.caudal.application.port.out.SecretCipherPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Encryption of TOTP secrets. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MfaProperties.class)
class MfaConfig {

  @Bean
  SecretCipherPort secretCipher(MfaProperties properties) {
    return new AesGcmSecretCipherAdapter(properties);
  }
}
