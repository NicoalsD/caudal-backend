package co.caudal.infrastructure.persistence;

import co.caudal.application.auth.UserProfile;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UserProfilePort;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persistence adapter of the accounts ({@code iam.users}). */
@Component
public class UserPersistenceAdapter implements TokenVersionPort, UserProfilePort {

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
}
