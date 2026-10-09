package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.MembershipAdminPort;
import java.sql.Timestamp;
import java.time.Instant;
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
}
