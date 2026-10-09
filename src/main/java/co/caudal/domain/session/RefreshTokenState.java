package co.caudal.domain.session;

import java.time.Instant;

/**
 * Lifecycle of a refresh token as explicit states. Each state decides which transitions are legal:
 *
 * <ul>
 *   <li>{@code ACTIVE} can be rotated (it becomes {@code ROTATED}) or revoked.
 *   <li>{@code ROTATED} was already used. Presenting it again is a reuse and the family is revoked.
 *   <li>{@code REVOKED} was closed for another reason (logout, password change, reuse...). Using it
 *       reports a revoked session.
 * </ul>
 *
 * @pattern P18 State
 */
public interface RefreshTokenState {

  /**
   * Code of the state.
   *
   * @return {@code ACTIVE}, {@code ROTATED} or {@code REVOKED}
   */
  String code();

  /**
   * Checks that the token may be exchanged for a new one.
   *
   * @throws RefreshTokenReuseException if it was already rotated
   * @throws SessionRevokedException if it was revoked for another reason
   */
  void ensureRotatable();

  /**
   * Moves to the state after a rotation.
   *
   * @return the {@code ROTATED} state
   * @throws RefreshTokenReuseException if it was already rotated
   * @throws SessionRevokedException if it was revoked for another reason
   */
  RefreshTokenState rotate();

  /**
   * Moves to the state after a revocation. Revoking a token that is no longer active changes
   * nothing: it is already out of use.
   *
   * @param reason why the token is revoked
   * @return the resulting state
   */
  RefreshTokenState revoke(RevocationReason reason);

  /**
   * Reads the state from the columns of the row.
   *
   * @param revokedAt moment of revocation, null for an active token
   * @param reason reason of revocation, null for an active token
   * @return the state that the columns describe
   */
  static RefreshTokenState of(Instant revokedAt, RevocationReason reason) {
    if (revokedAt == null) {
      return ActiveRefreshTokenState.INSTANCE;
    }
    return reason == RevocationReason.ROTATED
        ? RotatedRefreshTokenState.INSTANCE
        : new RevokedRefreshTokenState(reason);
  }
}
