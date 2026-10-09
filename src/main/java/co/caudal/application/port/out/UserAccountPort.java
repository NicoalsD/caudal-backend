package co.caudal.application.port.out;

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
}
