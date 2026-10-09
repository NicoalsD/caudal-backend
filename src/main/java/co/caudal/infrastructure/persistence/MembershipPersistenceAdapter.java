package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.MembershipPort;
import co.caudal.domain.user.Membership;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads {@code iam.memberships}. The table has row level security: inside a transaction opened with
 * the session scope, the policy shows the rows of the aqueduct in {@code app.aqueduct_id} and the
 * account's own rows in {@code app.user_id}. The query also filters by user and validity, so the
 * result never depends on the policy alone.
 */
@Component
public class MembershipPersistenceAdapter implements MembershipPort {

  private static final String QUERY =
      "SELECT aqueduct_id, role_code, valid_from, valid_to FROM iam.memberships"
          + " WHERE user_id = :userId AND valid_from <= :now"
          + " AND (valid_to IS NULL OR valid_to > :now)"
          + " ORDER BY valid_from, aqueduct_id";

  private final JdbcClient jdbc;

  MembershipPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Membership> findActive(UUID userId, Instant now) {
    return jdbc.sql(QUERY)
        .param("userId", userId)
        .param("now", Timestamp.from(now))
        .query(
            (rs, row) -> {
              Timestamp validTo = rs.getTimestamp("valid_to");
              return new Membership(
                  rs.getObject("aqueduct_id", UUID.class),
                  rs.getString("role_code"),
                  rs.getTimestamp("valid_from").toInstant(),
                  validTo == null ? null : validTo.toInstant());
            })
        .list();
  }
}
