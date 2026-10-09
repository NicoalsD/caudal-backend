package co.caudal.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code iam.refresh_tokens}. Every query is parameterized. */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  /** Rotates a token only while nobody has revoked it; returns the number of rows changed. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update RefreshTokenEntity t set t.revokedAt = :now, t.revokedReason = :reason,"
          + " t.replacedById = :replacedById, t.lastUsedAt = :now"
          + " where t.id = :id and t.revokedAt is null")
  int rotate(
      @Param("id") UUID id,
      @Param("replacedById") UUID replacedById,
      @Param("reason") String reason,
      @Param("now") Instant now);

  /** Revokes every token of the family that is still usable; returns how many. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update RefreshTokenEntity t set t.revokedAt = :now, t.revokedReason = :reason"
          + " where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(
      @Param("familyId") UUID familyId, @Param("reason") String reason, @Param("now") Instant now);
}
