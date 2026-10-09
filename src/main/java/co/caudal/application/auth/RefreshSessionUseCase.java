package co.caudal.application.auth;

import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.Membership;
import java.time.Clock;
import java.util.List;
import java.util.OptionalInt;

/**
 * Renews a session with the refresh token (docs/Seguridad.md, section 2.3).
 *
 * <p>The presented token is rotated (see {@link RefreshTokenService}); then a new access token is
 * issued for the first membership in force, with the current token version of the account. The
 * active aqueduct of a session that was switched is not remembered: a refresh goes back to the
 * oldest membership in force.
 */
public class RefreshSessionUseCase {

  private static final String UNKNOWN_IP = "unknown";

  private final RefreshTokenService refreshTokens;
  private final TokenVersionPort versions;
  private final MembershipPort memberships;
  private final AccessTokenIssuerPort accessTokens;
  private final PrivacyHasherPort hasher;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param refreshTokens rotation of refresh tokens
   * @param versions current token version of accounts
   * @param memberships memberships
   * @param accessTokens access token issuer
   * @param hasher keyed hash for IPs
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RefreshSessionUseCase(
      RefreshTokenService refreshTokens,
      TokenVersionPort versions,
      MembershipPort memberships,
      AccessTokenIssuerPort accessTokens,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.refreshTokens = refreshTokens;
    this.versions = versions;
    this.memberships = memberships;
    this.accessTokens = accessTokens;
    this.hasher = hasher;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Renews the session.
   *
   * @param input the cookie value and caller data
   * @return a new access token and the rotated refresh token
   * @throws UnauthorizedException if the token is missing, unknown or expired, or the account can
   *     no longer sign in
   * @throws SessionRevokedException if the token was revoked or reused
   */
  public RefreshResult execute(RefreshInput input) {
    ClientContext client =
        new ClientContext(
            hasher.hmac(input.ipAddress() == null ? UNKNOWN_IP : input.ipAddress()),
            input.userAgent());
    RotatedRefreshToken rotated = refreshTokens.rotate(input.refreshSecret(), client);
    OptionalInt version = versions.findCurrentVersion(rotated.userId());
    if (version.isEmpty()) {
      throw new UnauthorizedException();
    }
    List<Membership> active =
        unitOfWork.executeAs(
            SessionScope.ofUser(rotated.userId()),
            () -> memberships.findActive(rotated.userId(), clock.instant()));
    if (active.isEmpty()) {
      throw new UnauthorizedException();
    }
    Membership membership = active.getFirst();
    IssuedAccessToken access =
        accessTokens.issue(
            new AccessTokenSubject(
                rotated.userId(), membership.role(), membership.aqueductId(), version.getAsInt()));
    return new RefreshResult(access, rotated.next());
  }
}
