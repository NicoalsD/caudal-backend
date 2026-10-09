package co.caudal.infrastructure.security;

import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.shared.FieldLimits;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the decoder that every authenticated request goes through.
 *
 * <p>The algorithm is fixed to HS256: {@code none} and any other algorithm are rejected before the
 * claims are read. Then it validates {@code exp} and {@code nbf} with a tolerance of 30 seconds,
 * {@code iss}, {@code aud}, the presence of the session claims and {@code tv} against the account.
 */
@Configuration(proxyBeanMethods = false)
class JwtDecoderConfig {

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties, Clock clock, TokenVersionPort versions) {
    return decoder(properties, clock, versions);
  }

  static NimbusJwtDecoder decoder(
      JwtProperties properties, Clock clock, TokenVersionPort versions) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(JwtConfig.signingKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(validators(properties, clock, versions));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> validators(
      JwtProperties properties, Clock clock, TokenVersionPort versions) {
    JwtTimestampValidator timestamps =
        new JwtTimestampValidator(Duration.ofSeconds(FieldLimits.JWT_CLOCK_SKEW_SECONDS));
    timestamps.setClock(Objects.requireNonNull(clock));
    return new DelegatingOAuth2TokenValidator<>(
        requiredClaim(JwtClaimNames.EXP),
        requiredClaim(JwtClaimNames.SUB),
        requiredClaim(JwtAccessTokenIssuerAdapter.CLAIM_ROLE),
        requiredClaim(JwtAccessTokenIssuerAdapter.CLAIM_AQUEDUCT_ID),
        timestamps,
        new JwtIssuerValidator(properties.issuer()),
        new JwtAudienceValidator(properties.audience()),
        new TokenVersionValidator(versions));
  }

  private static OAuth2TokenValidator<Jwt> requiredClaim(String name) {
    return new JwtClaimValidator<Object>(name, Objects::nonNull);
  }
}
