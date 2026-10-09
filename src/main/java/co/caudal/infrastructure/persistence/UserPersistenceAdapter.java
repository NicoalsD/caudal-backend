package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.LoginAccount;
import co.caudal.application.auth.UserProfile;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UserProfilePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persistence adapter of the accounts ({@code iam.users}). */
@Component
public class UserPersistenceAdapter implements TokenVersionPort, UserProfilePort, LoginAccountPort {

  /** Status of an account that was deactivated for good. */
  static final String STATUS_DISABLED = "DISABLED";

  private static final String PROFILE_QUERY =
      "SELECT u.id, u.username, u.full_name, u.status, u.must_change_password,"
          + " (SELECT max(p.version) FROM iam.privacy_acceptances p WHERE p.user_id = u.id)"
          + " AS privacy_version FROM iam.users u WHERE u.id = :id";

  private final UserJpaRepository users;
  private final JdbcClient jdbc;

  UserPersistenceAdapter(UserJpaRepository users, JdbcClient jdbc) {
    this.users = users;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UserProfile> findProfile(UUID userId) {
    return jdbc.sql(PROFILE_QUERY)
        .param("id", userId)
        .query(
            (rs, row) -> {
              int version = rs.getInt("privacy_version");
              return new UserProfile(
                  rs.getObject("id", UUID.class),
                  rs.getString("username"),
                  rs.getString("full_name"),
                  rs.getString("status"),
                  rs.getBoolean("must_change_password"),
                  rs.wasNull() ? null : version);
            })
        .optional();
  }

  @Override
  public OptionalInt findCurrentVersion(UUID userId) {
    return users
        .findTokenVersionUnlessDisabled(userId, STATUS_DISABLED)
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }

  @Override
  public Optional<LoginAccount> findByUsername(String username) {
    return users
        .findByUsername(username)
        .map(
            user ->
                new LoginAccount(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getPasswordHash(),
                    user.getStatus(),
                    user.getLockedUntil(),
                    user.getLockoutLevel(),
                    user.getTokenVersion(),
                    user.isMustChangePassword()));
  }

  @Override
  public void saveFailure(
      UUID userId, boolean locked, int failedCount, Instant lockedUntil, int lockoutLevel) {
    jdbc.sql(
            "UPDATE iam.users SET status = :status, failed_login_count = :failed,"
                + " locked_until = :lockedUntil, lockout_level = :level"
                + " WHERE id = :id AND status <> 'DISABLED'")
        .param("status", locked ? LoginAccount.LOCKED : LoginAccount.ACTIVE)
        .param("failed", failedCount)
        .param("lockedUntil", lockedUntil == null ? null : Timestamp.from(lockedUntil))
        .param("level", lockoutLevel)
        .param("id", userId)
        .update();
  }

  @Override
  public void saveSuccess(UUID userId, Instant now, String ipHmac) {
    jdbc.sql(
            "UPDATE iam.users SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL,"
                + " lockout_level = 0, last_login_at = :now, last_login_ip_hmac = :ip"
                + " WHERE id = :id AND status <> 'DISABLED'")
        .param("now", Timestamp.from(now))
        .param("ip", ipHmac)
        .param("id", userId)
        .update();
  }

  @Override
  public void updatePasswordHash(UUID userId, String passwordHash) {
    jdbc.sql("UPDATE iam.users SET password_hash = :hash WHERE id = :id")
        .param("hash", passwordHash)
        .param("id", userId)
        .update();
  }
}
