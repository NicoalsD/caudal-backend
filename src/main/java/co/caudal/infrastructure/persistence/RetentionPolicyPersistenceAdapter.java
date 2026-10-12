package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.RetentionPolicyPort;
import co.caudal.application.retention.RetentionPolicyView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stores retention periods in {@code audit.retention_policies}. */
@Component
public class RetentionPolicyPersistenceAdapter implements RetentionPolicyPort {

  private final JdbcClient jdbc;

  RetentionPolicyPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RetentionPolicyView> update(String dataClass, int retentionDays, UUID updatedBy) {
    return jdbc.sql(
            "UPDATE audit.retention_policies SET retention_days = :days, updated_by = :by,"
                + " updated_at = now() WHERE data_class = :dataClass"
                + " RETURNING data_class, retention_days, updated_by, updated_at")
        .param("days", retentionDays)
        .param("by", updatedBy)
        .param("dataClass", dataClass)
        .query(
            (rs, row) ->
                new RetentionPolicyView(
                    rs.getString("data_class"),
                    rs.getInt("retention_days"),
                    rs.getObject("updated_by", UUID.class),
                    SqlTime.instant(rs, "updated_at")))
        .optional();
  }
}
