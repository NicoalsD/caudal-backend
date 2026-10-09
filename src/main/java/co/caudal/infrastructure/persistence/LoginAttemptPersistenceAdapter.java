package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.LoginAttempt;
import co.caudal.application.port.out.LoginAttemptPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Records and counts {@code iam.login_attempts}. Only keyed hashes are stored. */
@Component
public class LoginAttemptPersistenceAdapter implements LoginAttemptPort {

  private static final String INSERT =
      "INSERT INTO iam.login_attempts (user_id, username_hmac, ip_hmac, succeeded, failure_reason)"
          + " VALUES (:userId, :usernameHmac, :ipHmac, :succeeded, :reason)";

  private static final String COUNT_BY_USER =
      "SELECT count(*) FROM iam.login_attempts a WHERE a.user_id = :userId"
          + " AND NOT a.succeeded AND a.failure_reason IN ('INVALID_CREDENTIALS', 'MFA_FAILED')"
          + " AND a.created_at >= :since AND a.created_at > COALESCE((SELECT max(s.created_at)"
          + " FROM iam.login_attempts s WHERE s.user_id = :userId AND s.succeeded), '-infinity')";

  private static final String COUNT_BY_IP =
      "SELECT count(*) FROM iam.login_attempts WHERE ip_hmac = :ipHmac AND NOT succeeded"
          + " AND failure_reason <> 'RATE_LIMITED' AND created_at >= :since";

  private final JdbcClient jdbc;

  LoginAttemptPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(LoginAttempt attempt) {
    jdbc.sql(INSERT)
        .param("userId", attempt.userId())
        .param("usernameHmac", attempt.usernameHmac())
        .param("ipHmac", attempt.ipHmac())
        .param("succeeded", attempt.succeeded())
        .param("reason", attempt.failureReason() == null ? null : attempt.failureReason().name())
        .update();
  }

  @Override
  public int countConsecutiveFailures(UUID userId, Instant since) {
    return jdbc.sql(COUNT_BY_USER)
        .param("userId", userId)
        .param("since", Timestamp.from(since))
        .query(Integer.class)
        .single();
  }

  @Override
  public int countFailuresByIp(String ipHmac, Instant since) {
    return jdbc.sql(COUNT_BY_IP)
        .param("ipHmac", ipHmac)
        .param("since", Timestamp.from(since))
        .query(Integer.class)
        .single();
  }
}
