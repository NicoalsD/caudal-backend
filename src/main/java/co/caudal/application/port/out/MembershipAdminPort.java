package co.caudal.application.port.out;

import co.caudal.application.user.MembershipView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  /**
   * Tells whether an account exists.
   *
   * @param userId the account
   * @return true if {@code iam.users} has it
   */
  boolean userExists(UUID userId);

  /**
   * Tells whether the account already has a membership of the aqueduct whose validity overlaps the
   * given one (an account holds one role at a time in an aqueduct).
   *
   * @param userId the account
   * @param aqueductId the aqueduct
   * @param validFrom start of the new validity
   * @param validTo end of the new validity, null while open
   * @return true if there is an overlap
   */
  boolean overlaps(UUID userId, UUID aqueductId, Instant validFrom, Instant validTo);

  /**
   * Finds a membership of the aqueduct.
   *
   * @param aqueductId the aqueduct of the session
   * @param membershipId the membership
   * @return the membership, or empty
   */
  Optional<MembershipView> find(UUID aqueductId, UUID membershipId);

  /**
   * Lists memberships of the aqueduct ordered by id (creation order).
   *
   * @param aqueductId the aqueduct of the session
   * @param userId only this account, or null for all
   * @param afterId only ids after this one, or null from the start
   * @param limit maximum number of memberships
   * @return the memberships
   */
  List<MembershipView> list(UUID aqueductId, UUID userId, UUID afterId, int limit);

  /**
   * Ends a membership: sets {@code valid_to} to the given moment, or just after its start if it has
   * not started yet. The row is never deleted because the audit refers to it.
   *
   * @param membershipId the membership
   * @param at the moment it ends
   * @return the moment stored in {@code valid_to}
   */
  Instant close(UUID membershipId, Instant at);
}
