package co.caudal.application.auth;

import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RevocationReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** In-memory token store for use case tests; also records the client contexts it received. */
class InMemoryRefreshTokenPort implements RefreshTokenPort {

  final List<RefreshToken> tokens = new ArrayList<>();
  final List<ClientContext> clients = new ArrayList<>();

  @Override
  public RefreshToken save(RefreshToken token, ClientContext client) {
    RefreshToken saved =
        new RefreshToken(
            UUID.randomUUID(),
            token.userId(),
            token.familyId(),
            token.tokenHash(),
            token.issuedAt(),
            token.expiresAt(),
            null,
            null,
            null);
    tokens.add(saved);
    clients.add(client);
    return saved;
  }

  @Override
  public Optional<RefreshToken> findByHash(String tokenHash) {
    return tokens.stream().filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
  }

  @Override
  public boolean markRotated(UUID tokenId, UUID replacedById, Instant now) {
    for (int i = 0; i < tokens.size(); i++) {
      RefreshToken token = tokens.get(i);
      if (token.id().equals(tokenId)) {
        if (token.isRevoked()) {
          return false;
        }
        tokens.set(
            i,
            new RefreshToken(
                token.id(),
                token.userId(),
                token.familyId(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                now,
                RevocationReason.ROTATED,
                replacedById));
        return true;
      }
    }
    return false;
  }

  @Override
  public int revokeFamily(UUID familyId, RevocationReason reason, Instant now) {
    int revoked = 0;
    for (int i = 0; i < tokens.size(); i++) {
      RefreshToken token = tokens.get(i);
      if (token.familyId().equals(familyId) && !token.isRevoked()) {
        tokens.set(i, revokedCopy(token, now, reason));
        revoked++;
      }
    }
    return revoked;
  }

  /** Revokes one token directly, as a logout would. */
  void revoke(int index, RevocationReason reason, Instant now) {
    tokens.set(index, revokedCopy(tokens.get(index), now, reason));
  }

  private static RefreshToken revokedCopy(
      RefreshToken token, Instant now, RevocationReason reason) {
    return new RefreshToken(
        token.id(),
        token.userId(),
        token.familyId(),
        token.tokenHash(),
        token.issuedAt(),
        token.expiresAt(),
        now,
        reason,
        token.replacedById());
  }
}
