package co.caudal.domain.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A refresh token as stored: only the hash of the secret, never the secret.
 *
 * <p>All the tokens that descend from one login by rotation share a {@code familyId}. {@code
 * revokedReason} is the reason code of {@code iam.refresh_tokens.revoked_reason} ({@code ROTATED},
 * {@code REUSE_DETECTED}, {@code LOGOUT}, ...).
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
    String revokedReason,
    UUID replacedById) {

  /** Reason stored when a token is replaced by its successor. */
  public static final String REASON_ROTATED = "ROTATED";

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
