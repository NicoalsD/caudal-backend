package co.caudal.application.auth;

import java.util.UUID;

/**
 * Who a transaction acts for. The database uses it for row level security: {@code app.user_id} lets
 * an account read its own memberships before an aqueduct is chosen, and {@code app.aqueduct_id}
 * limits every tenant table to one aqueduct. Both come from validated data (the account being
 * authenticated or the access token), never from the request.
 *
 * @param userId the account, or null
 * @param aqueductId the active aqueduct, or null
 */
public record SessionScope(UUID userId, UUID aqueductId) {

  /**
   * Scope of an account that has not chosen an aqueduct yet (login, refresh).
   *
   * @param userId the account
   * @return the scope
   */
  public static SessionScope ofUser(UUID userId) {
    return new SessionScope(userId, null);
  }
}
