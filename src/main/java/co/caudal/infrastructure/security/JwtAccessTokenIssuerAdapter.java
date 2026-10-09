package co.caudal.infrastructure.security;

import co.caudal.application.auth.AccessTokenSubject;
import co.caudal.application.auth.IssuedAccessToken;
import co.caudal.application.port.out.AccessTokenIssuerPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Issues the access token as a JWT signed with HS256 (Nimbus).
 *
 * <p>Claims: {@code iss}, {@code aud}, {@code sub} (user id), {@code role}, {@code aqueduct_id},
 * {@code tv} (token version), {@code jti}, {@code iat} and {@code exp}. Nothing else travels in the
 * token: no name, no username, no permissions (those are read from the database).
 */
public class JwtAccessTokenIssuerAdapter implements AccessTokenIssuerPort {

  /** Claim with the role code in the active aqueduct. */
  public static final String CLAIM_ROLE = "role";

  /** Claim with the active aqueduct id. */
  public static final String CLAIM_AQUEDUCT_ID = "aqueduct_id";

  /** Claim with the token version of the account. */
  public static final String CLAIM_TOKEN_VERSION = "tv";

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final Clock clock;

  /**
   * Creates the issuer.
   *
   * @param encoder signs with the configured HS256 key
   * @param properties issuer, audience and lifetime
   * @param clock source of time
   */
  public JwtAccessTokenIssuerAdapter(JwtEncoder encoder, JwtProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public IssuedAccessToken issue(AccessTokenSubject subject) {
    Instant issuedAt = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(subject.userId().toString())
            .claim(CLAIM_ROLE, subject.role())
            .claim(CLAIM_AQUEDUCT_ID, subject.aqueductId().toString())
            .claim(CLAIM_TOKEN_VERSION, subject.tokenVersion())
            .id(UUID.randomUUID().toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTtl()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedAccessToken(token, properties.accessTtl());
  }
}
