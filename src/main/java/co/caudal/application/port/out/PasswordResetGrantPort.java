package co.caudal.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One-time codes to set a password ({@code iam.password_reset_grants}). */
public interface PasswordResetGrantPort {

  /**
   * Stores a new grant. Only the hash of the code is kept.
   *
   * @param userId the account that can use the code
   * @param codeHash SHA-256 of the code
   * @param issuedBy who issued it
   * @param expiresAt when it stops working
   * @return the id of the grant
   */
  UUID save(UUID userId, String codeHash, UUID issuedBy, Instant expiresAt);

  /**
   * Finds a grant of the account that can still be used.
   *
   * @param userId the account
   * @param codeHash SHA-256 of the typed code
   * @param now the current instant
   * @return the id of the grant, or empty if there is none, or it was used, revoked or expired
   */
  Optional<UUID> findUsable(UUID userId, String codeHash, Instant now);

  /**
   * Marks a grant as used, only if nobody used or revoked it before.
   *
   * @param grantId the grant
   * @param now moment of the use
   * @return true if this call used it; false if it was no longer available (a concurrent use)
   */
  boolean markUsed(UUID grantId, Instant now);

  /**
   * Revokes the codes of an account that were neither used nor revoked.
   *
   * @param userId the account
   * @param now moment of the revocation
   * @return how many codes were revoked
   */
  int revokeOpen(UUID userId, Instant now);
}
