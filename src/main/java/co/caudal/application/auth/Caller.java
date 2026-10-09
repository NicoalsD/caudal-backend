package co.caudal.application.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Who sends a command, taken from the validated access token and the connection, never from the
 * body of the request.
 *
 * @param userId the account
 * @param aqueductId the active aqueduct of the session
 * @param role the role of the session
 * @param ipAddress the address of the connection, null if unknown
 */
public record Caller(UUID userId, UUID aqueductId, String role, String ipAddress) {

  /** Validates the parts that every caller has. */
  public Caller {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(aqueductId, "aqueductId");
  }

  /**
   * Scope of a transaction that acts for this caller, for row level security.
   *
   * @return the scope of the caller's account and aqueduct
   */
  public SessionScope scope() {
    return new SessionScope(userId, aqueductId);
  }
}
