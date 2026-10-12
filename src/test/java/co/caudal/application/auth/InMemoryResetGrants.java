package co.caudal.application.auth;

import co.caudal.application.port.out.PasswordResetGrantPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Keeps the reset grants in memory for use case tests. */
class InMemoryResetGrants implements PasswordResetGrantPort {

  static final class Grant {
    final UUID id = UUID.randomUUID();
    final UUID userId;
    final String codeHash;
    final UUID issuedBy;
    final Instant expiresAt;
    Instant usedAt;

    Grant(UUID userId, String codeHash, UUID issuedBy, Instant expiresAt) {
      this.userId = userId;
      this.codeHash = codeHash;
      this.issuedBy = issuedBy;
      this.expiresAt = expiresAt;
    }
  }

  final List<Grant> grants = new ArrayList<>();

  @Override
  public UUID save(UUID userId, String codeHash, UUID issuedBy, Instant expiresAt) {
    Grant grant = new Grant(userId, codeHash, issuedBy, expiresAt);
    grants.add(grant);
    return grant.id;
  }

  @Override
  public Optional<UUID> findUsable(UUID userId, String codeHash, Instant now) {
    return grants.stream()
        .filter(g -> g.userId.equals(userId) && g.codeHash.equals(codeHash))
        .filter(g -> g.usedAt == null && g.expiresAt.isAfter(now))
        .map(g -> g.id)
        .findFirst();
  }

  @Override
  public boolean markUsed(UUID grantId, Instant now) {
    for (Grant grant : grants) {
      if (grant.id.equals(grantId) && grant.usedAt == null) {
        grant.usedAt = now;
        return true;
      }
    }
    return false;
  }
}
