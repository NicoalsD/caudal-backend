package co.caudal.application.auth;

import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues refresh tokens and rotates them (docs/Seguridad.md, section 2.3).
 *
 * <p>Every use of a token replaces it: the presented token is marked as rotated and a new one is
 * issued in the same family with the same lifetime. Only the SHA-256 of each secret is stored.
 */
public class RefreshTokenService {

  private final RefreshTokenPort tokens;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param tokens storage of the tokens
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RefreshTokenService(RefreshTokenPort tokens, UnitOfWorkPort unitOfWork, Clock clock) {
    this.tokens = tokens;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Starts a session: a new token in a new family.
   *
   * @param userId owner of the session
   * @param timeToLive how long the session lasts
   * @param client network context of the request
   * @return the token for the cookie
   */
  public IssuedRefreshToken issueNewFamily(UUID userId, Duration timeToLive, ClientContext client) {
    return unitOfWork.execute(
        () -> store(userId, UUID.randomUUID(), timeToLive, client).toIssued());
  }

  /**
   * Replaces the presented token by a new one in the same family.
   *
   * @param presentedSecret the raw value from the cookie
   * @param client network context of the request
   * @return the owner of the session and the new token
   * @throws UnauthorizedException if the token is unknown, expired or no longer usable
   */
  public RotatedRefreshToken rotate(String presentedSecret, ClientContext client) {
    if (!OpaqueToken.isPlausible(presentedSecret)) {
      throw new UnauthorizedException();
    }
    String hash = OpaqueToken.hashOf(presentedSecret);
    Optional<RotatedRefreshToken> rotated =
        unitOfWork.execute(() -> rotateInTransaction(hash, client));
    return rotated.orElseThrow(UnauthorizedException::new);
  }

  private Optional<RotatedRefreshToken> rotateInTransaction(String hash, ClientContext client) {
    Optional<RefreshToken> found = tokens.findByHash(hash);
    Instant now = clock.instant();
    if (found.isEmpty() || found.get().isExpired(now) || found.get().isRevoked()) {
      return Optional.empty();
    }
    RefreshToken current = found.get();
    Stored next = store(current.userId(), current.familyId(), current.lifetime(), client);
    if (!tokens.markRotated(current.id(), next.saved().id(), now)) {
      return Optional.empty();
    }
    return Optional.of(new RotatedRefreshToken(current.userId(), next.toIssued()));
  }

  private Stored store(UUID userId, UUID familyId, Duration timeToLive, ClientContext client) {
    OpaqueToken secret = OpaqueToken.generate();
    RefreshToken token = RefreshToken.issue(userId, familyId, secret, clock.instant(), timeToLive);
    RefreshToken saved = tokens.save(token, client);
    return new Stored(secret, saved);
  }

  private record Stored(OpaqueToken secret, RefreshToken saved) {

    IssuedRefreshToken toIssued() {
      return new IssuedRefreshToken(secret.raw(), saved.expiresAt());
    }
  }
}
