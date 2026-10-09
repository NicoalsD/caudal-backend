package co.caudal.application.port.out;

import co.caudal.application.auth.ClientContext;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RevocationReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores refresh tokens by the hash of their secret. */
public interface RefreshTokenPort {

  /**
   * Saves a new token.
   *
   * @param token the token to save (without id)
   * @param client the network context of the request
   * @return the saved token, with its id
   */
  RefreshToken save(RefreshToken token, ClientContext client);

  /**
   * Looks a token up by the hash of its secret.
   *
   * @param tokenHash lower case hexadecimal SHA-256
   * @return the token, or empty if no token has that hash
   */
  Optional<RefreshToken> findByHash(String tokenHash);

  /**
   * Marks a token as rotated, only if nobody revoked it before.
   *
   * @param tokenId the token being replaced
   * @param replacedById its successor
   * @param now moment of the rotation
   * @return true if this call rotated it; false if it was already revoked (a concurrent use)
   */
  boolean markRotated(UUID tokenId, UUID replacedById, Instant now);

  /**
   * Revokes every token of a family that is still usable.
   *
   * @param familyId the rotation family
   * @param reason why they are revoked
   * @param now moment of the revocation
   * @return how many tokens were revoked
   */
  int revokeFamily(UUID familyId, RevocationReason reason, Instant now);
}
