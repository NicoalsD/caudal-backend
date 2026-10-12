package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.PasswordResetGrantPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persistence adapter of {@code iam.password_reset_grants}. */
@Component
public class PasswordResetGrantPersistenceAdapter implements PasswordResetGrantPort {

  private final JdbcClient jdbc;

  PasswordResetGrantPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UUID save(UUID userId, String codeHash, UUID issuedBy, Instant expiresAt) {
    return jdbc.sql(
            "INSERT INTO iam.password_reset_grants (user_id, code_hash, issued_by, expires_at)"
                + " VALUES (:userId, :codeHash, :issuedBy, :expiresAt) RETURNING id")
        .param("userId", userId)
        .param("codeHash", codeHash)
        .param("issuedBy", issuedBy)
        .param("expiresAt", Timestamp.from(expiresAt))
        .query(UUID.class)
        .single();
  }

  @Override
  public Optional<UUID> findUsable(UUID userId, String codeHash, Instant now) {
    return jdbc.sql(
            "SELECT id FROM iam.password_reset_grants WHERE user_id = :userId"
                + " AND code_hash = :codeHash AND used_at IS NULL AND revoked_at IS NULL"
                + " AND expires_at > :now")
        .param("userId", userId)
        .param("codeHash", codeHash)
        .param("now", Timestamp.from(now))
        .query(UUID.class)
        .optional();
  }

  @Override
  public boolean markUsed(UUID grantId, Instant now) {
    return jdbc.sql(
                "UPDATE iam.password_reset_grants SET used_at = :now WHERE id = :id"
                    + " AND used_at IS NULL AND revoked_at IS NULL")
            .param("now", Timestamp.from(now))
            .param("id", grantId)
            .update()
        == 1;
  }
}
