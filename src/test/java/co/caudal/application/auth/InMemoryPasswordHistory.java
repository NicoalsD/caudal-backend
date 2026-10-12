package co.caudal.application.auth;

import co.caudal.application.port.out.PasswordHistoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Keeps the previous hashes in memory for use case tests. */
class InMemoryPasswordHistory implements PasswordHistoryPort {

  record Entry(UUID userId, String hash) {}

  final List<Entry> entries = new ArrayList<>();

  @Override
  public List<String> findRecent(UUID userId, int limit) {
    return entries.reversed().stream()
        .filter(entry -> entry.userId().equals(userId))
        .limit(Math.max(limit, 0))
        .map(Entry::hash)
        .toList();
  }

  @Override
  public void add(UUID userId, String passwordHash) {
    entries.add(new Entry(userId, passwordHash));
  }
}
