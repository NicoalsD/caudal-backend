package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.ClientContext;
import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.domain.session.RefreshToken;
import co.caudal.domain.session.RevocationReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Persistence adapter of the refresh tokens ({@code iam.refresh_tokens}). */
@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

  private final RefreshTokenJpaRepository tokens;

  RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  @Override
  public RefreshToken save(RefreshToken token, ClientContext client) {
    RefreshTokenEntity saved =
        tokens.saveAndFlush(
            new RefreshTokenEntity(
                token.userId(),
                token.familyId(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                client.ipHmac(),
                client.userAgent()));
    return toDomain(saved);
  }

  @Override
  public Optional<RefreshToken> findByHash(String tokenHash) {
    return tokens.findByTokenHash(tokenHash).map(RefreshTokenPersistenceAdapter::toDomain);
  }

  @Override
  public boolean markRotated(UUID tokenId, UUID replacedById, Instant now) {
    return tokens.rotate(tokenId, replacedById, RevocationReason.ROTATED.name(), now) == 1;
  }

  @Override
  public int revokeFamily(UUID familyId, RevocationReason reason, Instant now) {
    return tokens.revokeFamily(familyId, reason.name(), now);
  }

  private static RefreshToken toDomain(RefreshTokenEntity entity) {
    return new RefreshToken(
        entity.getId(),
        entity.getUserId(),
        entity.getFamilyId(),
        entity.getTokenHash(),
        entity.getIssuedAt(),
        entity.getExpiresAt(),
        entity.getRevokedAt(),
        entity.getRevokedReason() == null
            ? null
            : RevocationReason.valueOf(entity.getRevokedReason()),
        entity.getReplacedById());
  }
}
