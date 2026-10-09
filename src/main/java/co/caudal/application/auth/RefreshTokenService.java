package co.caudal.application.auth;

import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.domain.session.OpaqueToken;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RefreshTokenReuseException;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.SessionRevokedException;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.shared.error.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues refresh tokens, rotates them and defends the session against copied tokens
 * (docs/Seguridad.md, section 2.3).
 *
 * <p>Every use of a token replaces it: the presented token is marked as rotated and a new one is
 * issued in the same family with the same lifetime. Only the SHA-256 of each secret is stored. If a
 * token that was already rotated is presented again, the secret was copied: the whole family is
 * revoked ({@code REUSE_DETECTED}), {@code TOKEN_REUSE_DETECTED} is recorded and the answer is
 * {@code SESSION_REVOKED}. The revocation is committed first and the error is raised afterwards,
 * otherwise the rollback would undo the defense.
 */
public class RefreshTokenService {

  private final RefreshTokenPort tokens;
  private final SecurityEventPort events;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param tokens storage of the tokens
   * @param events destination of the security events
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RefreshTokenService(
      RefreshTokenPort tokens, SecurityEventPort events, UnitOfWorkPort unitOfWork, Clock clock) {
    this.tokens = tokens;
    this.events = events;
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
   * @throws UnauthorizedException if the token is unknown or expired
   * @throws SessionRevokedException if the token was revoked or reused; a reuse also revokes the
   *     whole family
   */
  public RotatedRefreshToken rotate(String presentedSecret, ClientContext client) {
    if (!OpaqueToken.isPlausible(presentedSecret)) {
      throw new UnauthorizedException();
    }
    String hash = OpaqueToken.hashOf(presentedSecret);
    Attempt attempt = unitOfWork.execute(() -> attempt(hash, client));
    if (attempt.rejection() != null) {
      throw attempt.rejection();
    }
    return attempt.rotated();
  }

  private Attempt attempt(String hash, ClientContext client) {
    Optional<RefreshToken> found = tokens.findByHash(hash);
    if (found.isEmpty()) {
      return Attempt.rejected(new UnauthorizedException());
    }
    RefreshToken current = found.get();
    Instant now = clock.instant();
    try {
      current.ensureRotatable(now);
    } catch (RefreshTokenReuseException reuse) {
      return reuseDetected(current, client, now, reuse);
    } catch (DomainException rejected) {
      return Attempt.rejected(rejected);
    }
    Stored next = store(current.userId(), current.familyId(), current.lifetime(), client);
    if (!tokens.markRotated(current.id(), next.saved().id(), now)) {
      // Another request rotated the same token first: treated as a reuse, to be safe.
      return reuseDetected(current, client, now, new RefreshTokenReuseException());
    }
    return Attempt.rotated(new RotatedRefreshToken(current.userId(), next.toIssued()));
  }

  private Attempt reuseDetected(
      RefreshToken token, ClientContext client, Instant now, SessionRevokedException answer) {
    tokens.revokeFamily(token.familyId(), RevocationReason.REUSE_DETECTED, now);
    events.record(
        new SecurityEvent(
            SecurityEventType.TOKEN_REUSE_DETECTED,
            Severity.HIGH,
            token.userId(),
            null,
            client.ipHmac(),
            Map.of("family_id", token.familyId().toString())));
    return Attempt.rejected(answer);
  }

  private Stored store(UUID userId, UUID familyId, Duration timeToLive, ClientContext client) {
    OpaqueToken secret = OpaqueToken.generate();
    RefreshToken token = RefreshToken.issue(userId, familyId, secret, clock.instant(), timeToLive);
    RefreshToken saved = tokens.save(token, client);
    return new Stored(secret, saved);
  }

  private record Stored(OpaqueToken secret, RefreshToken saved) {

    IssuedRefreshToken toIssued() {
      return new IssuedRefreshToken(secret.raw(), saved.expiresAt(), saved.lifetime());
    }
  }

  /** Result of one rotation attempt, decided inside the transaction and acted on after it. */
  private record Attempt(RotatedRefreshToken rotated, DomainException rejection) {

    static Attempt rotated(RotatedRefreshToken rotated) {
      return new Attempt(rotated, null);
    }

    static Attempt rejected(DomainException rejection) {
      return new Attempt(null, rejection);
    }
  }
}
