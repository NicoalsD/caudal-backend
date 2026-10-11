package co.caudal.infrastructure.persistence;

import co.caudal.application.mfa.MfaFactor;
import co.caudal.application.mfa.SealedSecret;
import co.caudal.application.port.out.MfaFactorPort;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code iam.mfa_factors}. */
@Component
public class MfaFactorPersistenceAdapter implements MfaFactorPort {

  private final JdbcClient jdbc;

  MfaFactorPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<MfaFactor> find(UUID userId) {
    return jdbc.sql(
            "SELECT id, user_id, secret_ciphertext, key_id, confirmed_at, last_used_step"
                + " FROM iam.mfa_factors WHERE user_id = :user")
        .param("user", userId)
        .query(
            (rs, row) -> {
              long step = rs.getLong("last_used_step");
              Long lastUsedStep = rs.wasNull() ? null : step;
              return new MfaFactor(
                  rs.getObject("id", UUID.class),
                  rs.getObject("user_id", UUID.class),
                  new SealedSecret(rs.getBytes("secret_ciphertext"), rs.getString("key_id")),
                  SqlTime.instant(rs, "confirmed_at"),
                  lastUsedStep);
            })
        .optional();
  }

  @Override
  public void insert(UUID userId, SealedSecret secret) {
    jdbc.sql(
            "INSERT INTO iam.mfa_factors (user_id, secret_ciphertext, key_id)"
                + " VALUES (:user, :secret, :keyId)")
        .param("user", userId)
        .param("secret", secret.ciphertext())
        .param("keyId", secret.keyId())
        .update();
  }

  @Override
  public void deleteUnconfirmed(UUID userId) {
    jdbc.sql("DELETE FROM iam.mfa_factors WHERE user_id = :user AND confirmed_at IS NULL")
        .param("user", userId)
        .update();
  }

  @Override
  public void confirm(UUID factorId, Instant at, long step) {
    jdbc.sql(
            "UPDATE iam.mfa_factors SET confirmed_at = :at, last_used_step = :step"
                + " WHERE id = :id AND confirmed_at IS NULL")
        .param("at", SqlTime.offset(at), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("step", step)
        .param("id", factorId)
        .update();
  }

  @Override
  public boolean markUsed(UUID factorId, long step) {
    return jdbc.sql(
                "UPDATE iam.mfa_factors SET last_used_step = :step"
                    + " WHERE id = :id AND (last_used_step IS NULL OR last_used_step < :step)")
            .param("step", step)
            .param("id", factorId)
            .update()
        == 1;
  }
}
