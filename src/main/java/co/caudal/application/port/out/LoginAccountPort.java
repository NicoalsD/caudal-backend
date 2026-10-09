package co.caudal.application.port.out;

import co.caudal.application.auth.LoginAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads and updates the login state of accounts. */
public interface LoginAccountPort {

  /**
   * Finds an account by its normalized username.
   *
   * @param username the normalized username
   * @return the account, or empty
   */
  Optional<LoginAccount> findByUsername(String username);

  /**
   * Stores the failure counter and the lock of an account.
   *
   * @param userId the account
   * @param locked true to set the status {@code LOCKED}
   * @param failedCount consecutive failures
   * @param lockedUntil end of the lock, null if not locked
   * @param lockoutLevel level after the decision
   */
  void saveFailure(
      UUID userId, boolean locked, int failedCount, Instant lockedUntil, int lockoutLevel);

  /**
   * Stores a successful login: clears counters and lock and records time and IP hash.
   *
   * @param userId the account
   * @param now moment of the login
   * @param ipHmac keyed hash of the IP
   */
  void saveSuccess(UUID userId, Instant now, String ipHmac);

  /**
   * Replaces the stored password hash (rehash after a parameter change).
   *
   * @param userId the account
   * @param passwordHash the new hash
   */
  void updatePasswordHash(UUID userId, String passwordHash);
}
