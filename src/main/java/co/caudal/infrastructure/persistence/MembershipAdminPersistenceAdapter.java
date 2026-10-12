package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.user.MembershipView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Writes {@code iam.memberships}. The table has row level security with a {@code WITH CHECK} on the
 * aqueduct of the session, so a membership can only be created in the aqueduct of the transaction's
 * scope; the database refuses any other.
 */
@Component
public class MembershipAdminPersistenceAdapter implements MembershipAdminPort {

  private static final String COLUMNS =
      "SELECT id, user_id, aqueduct_id, role_code, valid_from, valid_to, granted_by"
          + " FROM iam.memberships";

  private final JdbcClient jdbc;

  MembershipAdminPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean roleExists(String roleCode) {
    return jdbc.sql("SELECT count(*) FROM iam.roles WHERE code = :code")
            .param("code", roleCode)
            .query(Integer.class)
            .single()
        > 0;
  }

  @Override
  public UUID insert(
      UUID userId,
      UUID aqueductId,
      String roleCode,
      Instant validFrom,
      Instant validTo,
      UUID grantedBy) {
    return jdbc.sql(
            "INSERT INTO iam.memberships (user_id, aqueduct_id, role_code, valid_from, valid_to,"
                + " granted_by) VALUES (:userId, :aqueductId, :roleCode, :validFrom, :validTo,"
                + " :grantedBy) RETURNING id")
        .param("userId", userId)
        .param("aqueductId", aqueductId)
        .param("roleCode", roleCode)
        .param("validFrom", Timestamp.from(validFrom))
        .param("validTo", validTo == null ? null : Timestamp.from(validTo))
        .param("grantedBy", grantedBy)
        .query(UUID.class)
        .single();
  }

  @Override
  public boolean userExists(UUID userId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM iam.users WHERE id = :id)")
        .param("id", userId)
        .query(Boolean.class)
        .single();
  }

  @Override
  public boolean overlaps(UUID userId, UUID aqueductId, Instant validFrom, Instant validTo) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM iam.memberships WHERE user_id = :user"
                + " AND aqueduct_id = :aqueduct AND tstzrange(valid_from, valid_to)"
                + " && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz)))")
        .param("user", userId)
        .param("aqueduct", aqueductId)
        .param("from", SqlTime.offset(validFrom), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(validTo), Types.TIMESTAMP_WITH_TIMEZONE)
        .query(Boolean.class)
        .single();
  }

  @Override
  public Optional<MembershipView> find(UUID aqueductId, UUID membershipId) {
    return jdbc.sql(COLUMNS + " WHERE id = :id AND aqueduct_id = :aqueduct")
        .param("id", membershipId)
        .param("aqueduct", aqueductId)
        .query(MembershipAdminPersistenceAdapter::view)
        .optional();
  }

  @Override
  public List<MembershipView> list(UUID aqueductId, UUID userId, UUID afterId, int limit) {
    return jdbc.sql(
            COLUMNS
                + " WHERE aqueduct_id = :aqueduct"
                + " AND (CAST(:user AS uuid) IS NULL OR user_id = :user)"
                + " AND (CAST(:after AS uuid) IS NULL OR id > :after)"
                + " ORDER BY id LIMIT :limit")
        .param("aqueduct", aqueductId)
        .param("user", userId, Types.OTHER)
        .param("after", afterId, Types.OTHER)
        .param("limit", limit)
        .query(MembershipAdminPersistenceAdapter::view)
        .list();
  }

  @Override
  public Instant close(UUID membershipId, Instant at) {
    return jdbc.sql(
            "UPDATE iam.memberships SET valid_to = greatest(CAST(:at AS timestamptz),"
                + " valid_from + interval '1 microsecond') WHERE id = :id RETURNING valid_to")
        .param("at", SqlTime.offset(at), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("id", membershipId)
        .query((rs, row) -> SqlTime.instant(rs, "valid_to"))
        .single();
  }

  private static MembershipView view(ResultSet rs, int row) throws SQLException {
    return new MembershipView(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("aqueduct_id", UUID.class),
        rs.getString("role_code"),
        SqlTime.instant(rs, "valid_from"),
        SqlTime.instant(rs, "valid_to"),
        rs.getObject("granted_by", UUID.class));
  }
}
