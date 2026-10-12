package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.SummarySharePort;
import co.caudal.application.summary.NewSummaryShare;
import co.caudal.application.summary.SummaryShareView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code reporting.summary_shares}. Row level security limits the aqueduct. */
@Component
public class SummarySharePersistenceAdapter implements SummarySharePort {

  private static final String COLUMNS =
      "SELECT id, grantee_user_id, scope, period_from, period_to, reason, authorized_by,"
          + " authorized_at, expires_at, revoked_at, revoked_by FROM reporting.summary_shares";

  private final JdbcClient jdbc;

  SummarySharePersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UUID insert(UUID aqueductId, NewSummaryShare share) {
    return jdbc.sql(
            "INSERT INTO reporting.summary_shares (aqueduct_id, grantee_user_id, scope,"
                + " period_from, period_to, reason, authorized_by, expires_at) VALUES (:aqueduct,"
                + " :grantee, :scope, :from, :to, :reason, :by, :expires) RETURNING id")
        .param("aqueduct", aqueductId)
        .param("grantee", share.granteeUserId())
        .param("scope", share.scope())
        .param("from", share.periodFrom())
        .param("to", share.periodTo())
        .param("reason", share.reason())
        .param("by", share.authorizedBy())
        .param("expires", SqlTime.offset(share.expiresAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .query(UUID.class)
        .single();
  }

  @Override
  public Optional<SummaryShareView> find(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = :id")
        .param("id", id)
        .query(SummarySharePersistenceAdapter::view)
        .optional();
  }

  @Override
  public boolean revoke(UUID id, UUID revokedBy, Instant revokedAt) {
    return jdbc.sql(
                "UPDATE reporting.summary_shares SET revoked_at = :at, revoked_by = :by"
                    + " WHERE id = :id AND revoked_at IS NULL")
            .param("at", SqlTime.offset(revokedAt), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("by", revokedBy, Types.OTHER)
            .param("id", id)
            .update()
        == 1;
  }

  @Override
  public List<SummaryShareView> listActive(
      UUID granteeUserId, LocalDate from, LocalDate to, Instant now, UUID beforeId, int limit) {
    return jdbc.sql(
            COLUMNS
                + " WHERE revoked_at IS NULL AND expires_at > :now"
                + " AND (CAST(:grantee AS uuid) IS NULL OR grantee_user_id = :grantee)"
                + " AND (CAST(:from AS date) IS NULL OR (period_from <= :from AND period_to >= :to))"
                + " AND (CAST(:before AS uuid) IS NULL OR id < :before)"
                + " ORDER BY id DESC LIMIT :limit")
        .param("now", SqlTime.offset(now), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("grantee", granteeUserId, Types.OTHER)
        .param("from", from, Types.DATE)
        .param("to", to, Types.DATE)
        .param("before", beforeId, Types.OTHER)
        .param("limit", limit)
        .query(SummarySharePersistenceAdapter::view)
        .list();
  }

  static SummaryShareView view(ResultSet rs, int row) throws SQLException {
    return new SummaryShareView(
        rs.getObject("id", UUID.class),
        rs.getObject("grantee_user_id", UUID.class),
        rs.getString("scope"),
        rs.getObject("period_from", LocalDate.class),
        rs.getObject("period_to", LocalDate.class),
        rs.getString("reason"),
        rs.getObject("authorized_by", UUID.class),
        SqlTime.instant(rs, "authorized_at"),
        SqlTime.instant(rs, "expires_at"),
        SqlTime.instant(rs, "revoked_at"),
        rs.getObject("revoked_by", UUID.class));
  }
}
