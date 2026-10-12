package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.MfaRecoveryCodePort;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code iam.mfa_recovery_codes}. */
@Component
public class MfaRecoveryCodePersistenceAdapter implements MfaRecoveryCodePort {

  private final JdbcClient jdbc;

  MfaRecoveryCodePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertAll(UUID userId, List<String> codeHashes) {
    for (String hash : codeHashes) {
      jdbc.sql("INSERT INTO iam.mfa_recovery_codes (user_id, code_hash) VALUES (:user, :hash)")
          .param("user", userId)
          .param("hash", hash)
          .update();
    }
  }

  @Override
  public boolean consume(UUID userId, String codeHash, Instant at) {
    return jdbc.sql(
                "UPDATE iam.mfa_recovery_codes SET used_at = :at"
                    + " WHERE user_id = :user AND code_hash = :hash AND used_at IS NULL")
            .param("at", SqlTime.offset(at), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("user", userId)
            .param("hash", codeHash)
            .update()
        == 1;
  }
}
