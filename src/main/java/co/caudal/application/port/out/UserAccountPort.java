package co.caudal.application.port.out;

import co.caudal.application.auth.LoginAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reads and changes the security state of accounts ({@code iam.users}). */
public interface UserAccountPort {

  /**
   * Raises the token version of an account by one, so every access token issued before stops
   * working at its next use.
   *
   * @param userId the account
   */
  void incrementTokenVersion(UUID userId);

  /**
   * Finds an account by id.
   *
   * @param userId the account
   * @return the account with its stored password hash, or empty
   */
  Optional<LoginAccount> findById(UUID userId);

  /**
   * Replaces the password hash and records when it changed.
   *
   * @param userId the account
   * @param passwordHash the new hash
   * @param changedAt moment of the change
   * @param mustChangePassword the value of the flag after the change
   */
  void replacePassword(
      UUID userId, String passwordHash, Instant changedAt, boolean mustChangePassword);

  /**
   * Creates an account unless the username is taken.
   *
   * @param username the normalized username
   * @param fullName the full name
   * @param passwordHash the hash of an unknowable password
   * @param createdBy the account that creates it
   * @return the id of the new account, or empty if the username already exists
   */
  Optional<UUID> insertIfAbsent(
      String username, String fullName, String passwordHash, UUID createdBy);
}
