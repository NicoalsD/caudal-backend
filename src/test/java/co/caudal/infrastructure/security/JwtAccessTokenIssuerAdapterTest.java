package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.auth.AccessTokenSubject;
import co.caudal.application.auth.IssuedAccessToken;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtAccessTokenIssuerAdapterTest {

  private static final String SECRET = "test-only-jwt-secret-with-at-least-32-bytes-0000";
  private static final JwtProperties PROPERTIES =
      new JwtProperties(SECRET, "caudal-test-issuer", "caudal-test-audience", 15);
  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final UUID USER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final AccessTokenSubject SUBJECT =
      new AccessTokenSubject(USER, "OPERATOR", AQUEDUCT, 3);

  private final JwtAccessTokenIssuerAdapter issuer =
      new JwtAccessTokenIssuerAdapter(
          new NimbusJwtEncoder(new ImmutableSecret<>(JwtConfig.signingKey(PROPERTIES))),
          PROPERTIES,
          CLOCK);

  @Test
  void issuesAnHs256TokenWithEveryClaimOfTheSession() {
    IssuedAccessToken issued = issuer.issue(SUBJECT);

    Jwt jwt = decodeIgnoringExpiry(issued.value());

    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.<String>getClaim("iss")).isEqualTo("caudal-test-issuer");
    assertThat(jwt.getAudience()).containsExactly("caudal-test-audience");
    assertThat(jwt.getSubject()).isEqualTo(USER.toString());
    assertThat(jwt.<String>getClaim("role")).isEqualTo("OPERATOR");
    assertThat(jwt.<String>getClaim("aqueduct_id")).isEqualTo(AQUEDUCT.toString());
    assertThat(jwt.<Long>getClaim("tv")).isEqualTo(3L);
    assertThat(jwt.getId()).isNotBlank();
    assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void carriesNothingElseThanTheDocumentedClaims() {
    Jwt jwt = decodeIgnoringExpiry(issuer.issue(SUBJECT).value());

    assertThat(jwt.getClaims().keySet())
        .containsExactlyInAnyOrder(
            "iss", "aud", "sub", "role", "aqueduct_id", "tv", "jti", "iat", "exp");
  }

  @Test
  void reportsTheLifetimeFromTheConfiguration() {
    assertThat(issuer.issue(SUBJECT).timeToLive()).isEqualTo(Duration.ofSeconds(900));
  }

  @Test
  void everyTokenHasItsOwnJti() {
    String first = decodeIgnoringExpiry(issuer.issue(SUBJECT).value()).getId();
    String second = decodeIgnoringExpiry(issuer.issue(SUBJECT).value()).getId();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void toStringDoesNotShowTheToken() {
    IssuedAccessToken issued = issuer.issue(SUBJECT);

    assertThat(issued.toString()).doesNotContain(issued.value());
  }

  @Test
  void aSecretShorterThan32BytesIsRefused() {
    String shortSecret = "x".repeat(31);

    assertThatThrownBy(() -> new JwtProperties(shortSecret, "iss", "aud", 15))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes")
        .hasMessageNotContaining(shortSecret);
  }

  @Test
  void aMissingSecretIsRefused() {
    assertThatThrownBy(() -> new JwtProperties(null, "iss", "aud", 15))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void issuerAudienceAndLifetimeAreRequired() {
    assertThatThrownBy(() -> new JwtProperties(SECRET, " ", "aud", 15))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtProperties(SECRET, "iss", null, 15))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtProperties(SECRET, "iss", "aud", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void propertiesNeverPrintTheSecret() {
    assertThat(PROPERTIES.toString()).doesNotContain(SECRET);
    assertThat(List.of(PROPERTIES).toString()).doesNotContain(SECRET);
  }

  /** The decoder checks expiry against the real clock; these tokens are fixed in the past. */
  private Jwt decodeIgnoringExpiry(String token) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(JwtConfig.signingKey(PROPERTIES))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(
        jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
    return decoder.decode(token);
  }
}
