package co.caudal.application.auth;

import co.caudal.application.port.out.UserAccountPort;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps the token versions in memory for use case tests. */
class InMemoryUserAccounts implements UserAccountPort {

  final Map<UUID, Integer> tokenVersions = new HashMap<>();

  @Override
  public void incrementTokenVersion(UUID userId) {
    tokenVersions.merge(userId, 1, Integer::sum);
  }
}
