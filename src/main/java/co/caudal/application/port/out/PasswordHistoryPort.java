package co.caudal.application.port.out;

import java.util.List;
import java.util.UUID;

/** Previous password hashes of an account ({@code iam.password_history}), append only. */
public interface PasswordHistoryPort {

  /**
   * Finds the most recent previous hashes, newest first. The table is never trimmed (the
   * application role cannot delete), so the size of the history is applied here when reading.
   *
   * @param userId the account
   * @param limit how many hashes to return at most
   * @return the hashes, newest first
   */
  List<String> findRecent(UUID userId, int limit);

  /**
   * Remembers a hash that stopped being the current one.
   *
   * @param userId the account
   * @param passwordHash the previous hash
   */
  void add(UUID userId, String passwordHash);
}
