package co.caudal.application.auth;

import co.caudal.application.port.out.UserAccountPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Keeps accounts in memory for use case tests. */
class InMemoryUserAccounts implements UserAccountPort {

  final Map<UUID, Integer> tokenVersions = new HashMap<>();
  final Map<UUID, LoginAccount> accounts = new HashMap<>();
  final Map<UUID, Boolean> mustChange = new HashMap<>();

  void add(LoginAccount account) {
    accounts.put(account.id(), account);
  }

  @Override
  public void incrementTokenVersion(UUID userId) {
    tokenVersions.merge(userId, 1, Integer::sum);
  }

  @Override
  public Optional<LoginAccount> findById(UUID userId) {
    return Optional.ofNullable(accounts.get(userId));
  }

  @Override
  public void replacePassword(
      UUID userId, String passwordHash, Instant changedAt, boolean mustChangePassword) {
    LoginAccount old = accounts.get(userId);
    accounts.put(
        userId,
        new LoginAccount(
            old.id(),
            old.username(),
            old.fullName(),
            passwordHash,
            old.status(),
            old.lockedUntil(),
            old.lockoutLevel(),
            old.tokenVersion(),
            mustChangePassword));
    mustChange.put(userId, mustChangePassword);
  }

  @Override
  public Optional<UUID> insertIfAbsent(
      String username, String fullName, String passwordHash, UUID createdBy) {
    boolean taken = accounts.values().stream().anyMatch(a -> a.username().equals(username));
    if (taken) {
      return Optional.empty();
    }
    UUID id = UUID.randomUUID();
    add(new LoginAccount(id, username, fullName, passwordHash, "ACTIVE", null, 0, 0, true));
    return Optional.of(id);
  }
}
