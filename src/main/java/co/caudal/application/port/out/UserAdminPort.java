package co.caudal.application.port.out;

import co.caudal.application.user.ManagedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and changes accounts for the Board ({@code iam.users}). It only reaches accounts that have
 * at least one membership, current or past, in the given aqueduct: an administrator never sees an
 * account of another community.
 */
public interface UserAdminPort {

  /**
   * Finds an account of the aqueduct.
   *
   * @param aqueductId the aqueduct of the administrator
   * @param userId the account
   * @return the account, or empty if it does not exist or has no membership in the aqueduct
   */
  Optional<ManagedUser> findInAqueduct(UUID aqueductId, UUID userId);

  /**
   * Lists the accounts of the aqueduct ordered by username.
   *
   * @param aqueductId the aqueduct of the administrator
   * @param status only this status, or null for all
   * @param afterUsername only usernames after this one, or null from the start
   * @param limit maximum number of accounts
   * @return the accounts
   */
  List<ManagedUser> listInAqueduct(UUID aqueductId, String status, String afterUsername, int limit);

  /**
   * Saves what an administrator changed.
   *
   * @param userId the account
   * @param fullName the full name
   * @param status the status
   * @param lockedUntil end of the lock, null if none or if the lock has no end
   * @param disabledAt moment of the deactivation if the account is disabled now and was not before;
   *     an earlier deactivation keeps its moment, and any other status clears it
   * @param clearFailures true to reset the failed logins and the lock level (an unlock)
   */
  void saveAdministration(
      UUID userId,
      String fullName,
      String status,
      Instant lockedUntil,
      Instant disabledAt,
      boolean clearFailures);
}
