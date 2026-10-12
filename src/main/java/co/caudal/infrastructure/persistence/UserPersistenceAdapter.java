package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.LoginAccount;
import co.caudal.application.auth.UserProfile;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.application.port.out.UserProfilePort;
import co.caudal.application.user.ManagedUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persistence adapter of the accounts ({@code iam.users}). */
@Component
public class UserPersistenceAdapter
    implements TokenVersionPort, UserProfilePort, LoginAccountPort, UserAccountPort, UserAdminPort {

  /** Status of an account that was deactivated for good. */
  static final String STATUS_DISABLED = "DISABLED";

  private static final String PROFILE_QUERY =
      "SELECT u.id, u.username, u.full_name, u.status, u.must_change_password,"
          + " (SELECT max(p.version) FROM iam.privacy_acceptances p WHERE p.user_id = u.id)"
          + " AS privacy_version FROM iam.users u WHERE u.id = :id";

  private static final String MANAGED_COLUMNS =
      "SELECT u.id, u.username, u.full_name, u.status, u.locked_until, u.must_change_password,"
          + " u.last_login_at FROM iam.users u";

  private static final String IN_AQUEDUCT =
      " EXISTS (SELECT 1 FROM iam.memberships m WHERE m.user_id = u.id"
          + " AND m.aqueduct_id = :aqueduct)";

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
    return users.findByUsername(username).map(UserPersistenceAdapter::toAccount);
  }

  @Override
  public Optional<LoginAccount> findById(UUID userId) {
    return users.findById(userId).map(UserPersistenceAdapter::toAccount);
  }

  private static LoginAccount toAccount(UserEntity user) {
    return new LoginAccount(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getPasswordHash(),
        user.getStatus(),
        user.getLockedUntil(),
        user.getLockoutLevel(),
        user.getTokenVersion(),
        user.isMustChangePassword());
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

  @Override
  public void incrementTokenVersion(UUID userId) {
    jdbc.sql(
            "UPDATE iam.users SET token_version = token_version + 1, updated_at = now()"
                + " WHERE id = :id")
        .param("id", userId)
        .update();
  }

  @Override
  public void replacePassword(
      UUID userId, String passwordHash, Instant changedAt, boolean mustChangePassword) {
    jdbc.sql(
            "UPDATE iam.users SET password_hash = :hash, password_changed_at = :changedAt,"
                + " must_change_password = :mustChange, updated_at = now() WHERE id = :id")
        .param("hash", passwordHash)
        .param("changedAt", Timestamp.from(changedAt))
        .param("mustChange", mustChangePassword)
        .param("id", userId)
        .update();
  }

  @Override
  public Optional<UUID> insertIfAbsent(
      String username, String fullName, String passwordHash, UUID createdBy) {
    // Must-change-password is true by default in the table. ON CONFLICT avoids an exception that
    // would leave the transaction aborted.
    return jdbc.sql(
            "INSERT INTO iam.users (username, full_name, password_hash, created_by)"
                + " VALUES (:username, :fullName, :hash, :createdBy)"
                + " ON CONFLICT (username) DO NOTHING RETURNING id")
        .param("username", username)
        .param("fullName", fullName)
        .param("hash", passwordHash)
        .param("createdBy", createdBy)
        .query(UUID.class)
        .optional();
  }

  @Override
  public Optional<ManagedUser> findInAqueduct(UUID aqueductId, UUID userId) {
    return jdbc.sql(MANAGED_COLUMNS + " WHERE u.id = :id AND" + IN_AQUEDUCT)
        .param("id", userId)
        .param("aqueduct", aqueductId)
        .query(UserPersistenceAdapter::managed)
        .optional();
  }

  @Override
  public List<ManagedUser> listInAqueduct(
      UUID aqueductId, String status, String afterUsername, int limit) {
    return jdbc.sql(
            MANAGED_COLUMNS
                + " WHERE"
                + IN_AQUEDUCT
                + " AND (CAST(:status AS varchar) IS NULL OR u.status = :status)"
                + " AND (CAST(:after AS varchar) IS NULL OR u.username > :after)"
                + " ORDER BY u.username LIMIT :limit")
        .param("aqueduct", aqueductId)
        .param("status", status, Types.VARCHAR)
        .param("after", afterUsername, Types.VARCHAR)
        .param("limit", limit)
        .query(UserPersistenceAdapter::managed)
        .list();
  }

  @Override
  public void saveAdministration(
      UUID userId,
      String fullName,
      String status,
      Instant lockedUntil,
      Instant disabledAt,
      boolean clearFailures) {
    jdbc.sql(
            "UPDATE iam.users SET full_name = :fullName, status = :status,"
                + " locked_until = :lockedUntil,"
                + " disabled_at = CASE WHEN :status = 'DISABLED'"
                + " THEN coalesce(disabled_at, :disabledAt) END,"
                + " failed_login_count = CASE WHEN :clear THEN 0 ELSE failed_login_count END,"
                + " lockout_level = CASE WHEN :clear THEN 0 ELSE lockout_level END,"
                + " updated_at = now() WHERE id = :id")
        .param("fullName", fullName)
        .param("status", status)
        .param("lockedUntil", SqlTime.offset(lockedUntil), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("disabledAt", SqlTime.offset(disabledAt), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("clear", clearFailures)
        .param("id", userId)
        .update();
  }

  private static ManagedUser managed(ResultSet rs, int row) throws SQLException {
    return new ManagedUser(
        rs.getObject("id", UUID.class),
        rs.getString("username"),
        rs.getString("full_name"),
        rs.getString("status"),
        SqlTime.instant(rs, "locked_until"),
        rs.getBoolean("must_change_password"),
        SqlTime.instant(rs, "last_login_at"));
  }
}
