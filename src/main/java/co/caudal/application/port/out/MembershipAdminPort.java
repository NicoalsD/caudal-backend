package co.caudal.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates and reads memberships for the administration of users. Every method must run in a
 * transaction opened with {@link UnitOfWorkPort#executeAs}: {@code iam.memberships} has row level
 * security, which only shows and accepts rows of the aqueduct in the session scope.
 */
public interface MembershipAdminPort {

  /**
   * Tells whether a role exists in the catalog.
   *
   * @param roleCode the role code
   * @return true if {@code iam.roles} has it
   */
  boolean roleExists(String roleCode);

  /**
   * Creates a membership.
   *
   * @param userId the account
   * @param aqueductId the aqueduct; it must be the one of the session scope
   * @param roleCode the role
   * @param validFrom start of the validity
   * @param validTo end of the validity, null while open
   * @param grantedBy who grants it
   * @return the id of the membership
   */
  UUID insert(
      UUID userId,
      UUID aqueductId,
      String roleCode,
      Instant validFrom,
      Instant validTo,
      UUID grantedBy);
}
