package co.caudal.infrastructure.security;

import co.caudal.application.port.out.PasswordHasherPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Wires the Argon2id password encoder and the port that the use cases see. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Argon2Properties.class)
class PasswordHashingConfig {

  @Bean
  PasswordEncoder passwordEncoder(Argon2Properties properties) {
    return Argon2PasswordHasherAdapter.encoderFor(properties);
  }

  @Bean
  PasswordHasherPort passwordHasher(PasswordEncoder encoder) {
    return new Argon2PasswordHasherAdapter(encoder);
  }
}
