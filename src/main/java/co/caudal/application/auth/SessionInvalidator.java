package co.caudal.application.auth;

import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.session.RevocationReason;
import java.time.Clock;
import java.util.UUID;

/**
 * Ends every session of an account at once (docs/API.md, section 1.2): it raises {@code
 * token_version}, which makes the access tokens already issued useless, and revokes every refresh
 * token with the reason of the change. A change of password, a change of role, a deactivation and
 * "close all sessions" all use it.
 *
 * <p>It joins the transaction of the caller, so the revocation and the change that caused it are
 * saved or undone together.
 */
public class SessionInvalidator {

  private final UserAccountPort accounts;
  private final RefreshTokenPort tokens;
  private final Clock clock;

  /**
   * Creates the invalidator.
   *
   * @param accounts accounts, to raise the token version
   * @param tokens refresh tokens, to revoke them
   * @param clock source of time
   */
  public SessionInvalidator(UserAccountPort accounts, RefreshTokenPort tokens, Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.clock = clock;
  }

  /**
   * Ends every session of the account.
   *
   * @param userId the account
   * @param reason why the refresh tokens are revoked
   * @return how many refresh tokens were revoked
   */
  public int invalidate(UUID userId, RevocationReason reason) {
    accounts.incrementTokenVersion(userId);
    return tokens.revokeAllOfUser(userId, reason, clock.instant());
  }
}
