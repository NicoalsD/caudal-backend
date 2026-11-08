package co.caudal.application.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Who sends a command, taken from the validated access token and the connection, never from the
 * body of the request.
 *
 * @param userId the account, null only for the autopilot
 * @param aqueductId the active aqueduct of the session
 * @param role the role of the session
 * @param ipAddress the address of the connection, null if unknown
 */
public record Caller(UUID userId, UUID aqueductId, String role, String ipAddress) {

  /** Role of the autopilot, the only caller without an account. */
  public static final String AUTOPILOT_ROLE = "AUTOPILOT";

  /** Validates the parts that every caller has. */
  public Caller {
    Objects.requireNonNull(aqueductId, "aqueductId");
    if (!AUTOPILOT_ROLE.equals(role)) {
      Objects.requireNonNull(userId, "userId");
    }
  }

  /**
   * The autopilot acting for an aqueduct. It has no account: its commands are audited with the
   * actor {@code SYSTEM}.
   *
   * @param aqueductId the aqueduct
   * @return the caller
   */
  public static Caller autopilot(UUID aqueductId) {
    return new Caller(null, aqueductId, AUTOPILOT_ROLE, null);
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
