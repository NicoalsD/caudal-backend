package co.caudal.application.user;

import java.time.Instant;
import java.util.UUID;

/**
 * A new account and the one-time code to activate it.
 *
 * @param id the account
 * @param username the normalized username
 * @param fullName the normalized full name
 * @param status the status of the account
 * @param mustChangePassword true until the first password change
 * @param membershipId the first membership
 * @param aqueductId the aqueduct of the membership
 * @param role the role of the membership
 * @param activationCode the code as shown to the person (only here, never stored)
 * @param activationExpiresAt when the code stops working
 */
public record CreatedUser(
    UUID id,
    String username,
    String fullName,
    String status,
    boolean mustChangePassword,
    UUID membershipId,
    UUID aqueductId,
    String role,
    String activationCode,
    Instant activationExpiresAt) {

  @Override
  public String toString() {
    return "CreatedUser[id=" + id + ", username=" + username + "]";
  }
}
