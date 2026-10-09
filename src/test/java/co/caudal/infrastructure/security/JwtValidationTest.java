package co.caudal.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.application.port.out.TokenVersionPort;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtValidationTest {

  private static final Instant NOW = Instant.parse("2026-10-09T13:00:00Z");
  private static final JwtProperties PROPERTIES =
      new JwtProperties(TestJwts.SECRET, TestJwts.ISSUER, TestJwts.AUDIENCE, 15);
  private static final UUID USER = UUID.fromString("0190f3a2-0000-7000-8000-000000000001");
  private static final UUID AQUEDUCT = UUID.fromString("0190f3a2-0000-7000-8000-0000000000aa");
  private static final int CURRENT_VERSION = 4;

  private OptionalInt storedVersion = OptionalInt.of(CURRENT_VERSION);
  private final TokenVersionPort versions = userId -> storedVersion;
  private final NimbusJwtDecoder decoder =
      JwtDecoderConfig.decoder(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC), versions);

  private JWTClaimsSet.Builder claims() {
    return TestJwts.validClaims(USER, "OPERATOR", AQUEDUCT, CURRENT_VERSION, NOW);
  }

  private String signed(JWTClaimsSet.Builder builder) {
    return TestJwts.sign(builder.build(), TestJwts.SECRET);
  }

  private void assertRejected(String token) {
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void acceptsAValidToken() {
    Jwt jwt = decoder.decode(signed(claims()));

    assertThat(jwt.getSubject()).isEqualTo(USER.toString());
    assertThat(jwt.<String>getClaim("role")).isEqualTo("OPERATOR");
  }

  @Test
  void rejectsAnUnsignedTokenWithAlgNone() {
    assertRejected(TestJwts.unsigned(claims().build()));
  }

  @Test
  void rejectsAnotherHmacAlgorithm() {
    assertRejected(TestJwts.sign(claims().build(), TestJwts.SECRET, JWSAlgorithm.HS384));
    assertRejected(TestJwts.sign(claims().build(), TestJwts.SECRET.repeat(2), JWSAlgorithm.HS512));
  }

  @Test
  void rejectsATokenSignedWithAnotherKey() {
    assertRejected(
        TestJwts.sign(claims().build(), "another-test-only-secret-with-32-bytes-or-more"));
  }

  @Test
  void rejectsGarbageAndEmptyTokens() {
    assertRejected("not-a-jwt");
    assertRejected("a.b.c");
    assertRejected("");
  }

  @Test
  void rejectsAWrongIssuer() {
    assertRejected(signed(claims().issuer("someone-else")));
  }

  @Test
  void rejectsAMissingIssuer() {
    assertRejected(signed(claims().issuer(null)));
  }

  @Test
  void rejectsAWrongAudience() {
    assertRejected(signed(claims().audience(List.of("another-app"))));
  }

  @Test
  void acceptsATokenWhenOneOfSeveralAudiencesMatches() {
    assertThatCode(
            () -> decoder.decode(signed(claims().audience(List.of("other", TestJwts.AUDIENCE)))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsAMissingAudience() {
    assertRejected(signed(claims().audience((String) null)));
  }

  @Test
  void rejectsAnExpiredTokenBeyondTheThirtySecondTolerance() {
    assertRejected(signed(claims().expirationTime(Date.from(NOW.minusSeconds(31)))));
  }

  @Test
  void acceptsAnExpiredTokenInsideTheThirtySecondTolerance() {
    assertThatCode(
            () ->
                decoder.decode(
                    signed(
                        claims()
                            .issueTime(Date.from(NOW.minusSeconds(900)))
                            .expirationTime(Date.from(NOW.minusSeconds(29))))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsATokenWithoutExpiration() {
    assertRejected(signed(claims().expirationTime(null)));
  }

  @Test
  void rejectsATokenThatIsNotValidYetBeyondTheTolerance() {
    assertRejected(signed(claims().notBeforeTime(Date.from(NOW.plusSeconds(31)))));
  }

  @Test
  void acceptsATokenThatStartsInsideTheTolerance() {
    assertThatCode(
            () -> decoder.decode(signed(claims().notBeforeTime(Date.from(NOW.plusSeconds(29))))))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsATokenWhoseTokenVersionIsOlderThanTheAccount() {
    storedVersion = OptionalInt.of(CURRENT_VERSION + 1);

    assertRejected(signed(claims()));
  }

  @Test
  void rejectsATokenWithoutTokenVersion() {
    assertRejected(signed(claims().claim("tv", null)));
  }

  @Test
  void rejectsATokenWhenTheAccountIsGoneOrDisabled() {
    storedVersion = OptionalInt.empty();

    assertRejected(signed(claims()));
  }

  @Test
  void rejectsASubjectThatIsNotAUserId() {
    assertRejected(signed(claims().subject("not-a-uuid")));
    assertRejected(signed(claims().subject(null)));
  }

  @Test
  void rejectsATokenWithoutRoleOrAqueduct() {
    assertRejected(signed(claims().claim("role", null)));
    assertRejected(signed(claims().claim("aqueduct_id", null)));
  }

  @Test
  void aSecretShorterThan32BytesPreventsStartup() {
    assertThatThrownBy(() -> new JwtProperties("short-secret", "iss", "aud", 15))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
