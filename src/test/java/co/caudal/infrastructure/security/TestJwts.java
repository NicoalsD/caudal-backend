package co.caudal.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Builds access tokens for tests, including forged ones (wrong algorithm, key or claims). */
public final class TestJwts {

  /** Secret of the test profile. */
  public static final String SECRET = "test-only-jwt-secret-with-at-least-32-bytes-0000";

  public static final String ISSUER = "caudal-test-issuer";
  public static final String AUDIENCE = "caudal-test-audience";

  private TestJwts() {}

  /** Claims of a valid token, ready to be changed before signing. */
  public static JWTClaimsSet.Builder validClaims(
      UUID userId, String role, UUID aqueductId, int tokenVersion, Instant issuedAt) {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(List.of(AUDIENCE))
        .subject(userId.toString())
        .claim("role", role)
        .claim("aqueduct_id", aqueductId.toString())
        .claim("tv", tokenVersion)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(issuedAt))
        .expirationTime(Date.from(issuedAt.plusSeconds(900)));
  }

  /** Signs with HS256 and the given secret. */
  public static String sign(JWTClaimsSet claims, String secret) {
    return sign(claims, secret, JWSAlgorithm.HS256);
  }

  /** Signs with the given HMAC algorithm and secret. */
  public static String sign(JWTClaimsSet claims, String secret, JWSAlgorithm algorithm) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
      jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
      return jwt.serialize();
    } catch (JOSEException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Builds an unsigned token with {@code alg: none}. */
  public static String unsigned(JWTClaimsSet claims) {
    return new PlainJWT(claims).serialize();
  }
}
