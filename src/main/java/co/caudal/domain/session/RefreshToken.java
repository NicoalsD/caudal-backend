package co.caudal.domain.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A refresh token as stored: only the hash of the secret, never the secret.
 *
 * <p>All the tokens that descend from one login by rotation share a {@code familyId}. {@code
 * revokedReason} is the reason of {@code iam.refresh_tokens.revoked_reason}. The lifecycle is
 * modeled by {@link RefreshTokenState}.
 *
 * @param id identifier, null until the token is saved
 * @param userId owner of the session
 * @param familyId rotation family
 * @param tokenHash lower case hexadecimal SHA-256 of the secret
 * @param issuedAt moment of issue
 * @param expiresAt moment after which it is not accepted
 * @param revokedAt moment it stopped being usable, null while it is usable
 * @param revokedReason why it stopped, null while it is usable
 * @param replacedById token that replaced it by rotation, null if it was not rotated
 */
public record RefreshToken(
    UUID id,
    UUID userId,
    UUID familyId,
    String tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    RevocationReason revokedReason,
    UUID replacedById) {

  /** Validates what every token needs. */
  public RefreshToken {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(familyId, "familyId");
    Objects.requireNonNull(tokenHash, "tokenHash");
    Objects.requireNonNull(issuedAt, "issuedAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }

  /**
   * Creates a token that has not been saved yet.
   *
   * @param userId owner of the session
   * @param familyId rotation family
   * @param secret the secret whose hash is kept
   * @param issuedAt moment of issue
   * @param timeToLive lifetime
   * @return an active token without id
   */
  public static RefreshToken issue(
      UUID userId, UUID familyId, OpaqueToken secret, Instant issuedAt, Duration timeToLive) {
    return new RefreshToken(
        null,
        userId,
        familyId,
        secret.hash(),
        issuedAt,
        issuedAt.plus(timeToLive),
        null,
        null,
        null);
  }

  /**
   * Tells whether the token is past its expiration.
   *
   * @param now the current instant
   * @return true if it expired
   */
  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  /**
   * State of the token in its lifecycle.
   *
   * @return {@code ACTIVE}, {@code ROTATED} or {@code REVOKED}
   */
  public RefreshTokenState state() {
    return RefreshTokenState.of(revokedAt, revokedReason);
  }

  /**
   * Checks that the token may be exchanged for a new one right now.
   *
   * @param now the current instant
   * @throws RefreshTokenReuseException if it was already rotated (the family must be revoked)
   * @throws SessionRevokedException if it was revoked for another reason
   * @throws UnauthorizedException if it expired
   */
  public void ensureRotatable(Instant now) {
    state().ensureRotatable();
    if (isExpired(now)) {
      throw new UnauthorizedException();
    }
  }

  /**
   * Tells whether the token was revoked, rotated or not.
   *
   * @return true if it is no longer usable because of a revocation
   */
  public boolean isRevoked() {
    return revokedAt != null;
  }

  /**
   * Length of the session this token grants, kept by its successors.
   *
   * @return the time between issue and expiration
   */
  public Duration lifetime() {
    return Duration.between(issuedAt, expiresAt);
  }

  @Override
  public String toString() {
    return "RefreshToken[id=" + id + ", familyId=" + familyId + ", revoked=" + isRevoked() + "]";
  }
}
