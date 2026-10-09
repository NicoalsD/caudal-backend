package co.caudal.infrastructure.security;

import co.caudal.application.port.out.AccessTokenIssuerPort;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Wires the HS256 key, the JWT encoder and the access token issuer. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

  /** Name of the JCA algorithm of the HS256 key. */
  static final String HMAC_SHA256 = "HmacSHA256";

  /**
   * Builds the signing key.
   *
   * @param properties validated JWT settings (the secret has at least 32 bytes)
   * @return the secret key for HS256
   */
  static SecretKey signingKey(JwtProperties properties) {
    return new SecretKeySpec(properties.secretBytes(), HMAC_SHA256);
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
  }

  @Bean
  AccessTokenIssuerPort accessTokenIssuer(
      JwtEncoder encoder, JwtProperties properties, Clock clock) {
    return new JwtAccessTokenIssuerAdapter(encoder, properties, clock);
  }
}
