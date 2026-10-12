package co.caudal.infrastructure.persistence;

import co.caudal.application.audit.AuditLogFilter;
import co.caudal.application.audit.AuditLogView;
import co.caudal.application.port.out.AuditLogQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code audit.audit_log}. The query names the aqueduct explicitly, on top of the row level
 * security, and never selects the request id or the HMAC of the address.
 */
@Component
public class AuditLogQueryPersistenceAdapter implements AuditLogQueryPort {

  private static final TypeReference<Map<String, Object>> STATE = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AuditLogQueryPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public List<AuditLogView> list(
      UUID aqueductId, AuditLogFilter filter, Instant before, Long beforeId, int limit) {
    return jdbc.sql(
            "SELECT id, occurred_at, actor_type, actor_id, action, entity_type, entity_id,"
                + " before_state::text AS before_state, after_state::text AS after_state,"
                + " prev_hash, row_hash FROM audit.audit_log WHERE aqueduct_id = :aqueduct"
                + " AND (CAST(:action AS varchar) IS NULL OR action = :action)"
                + " AND (CAST(:entityType AS varchar) IS NULL OR entity_type = :entityType)"
                + " AND (CAST(:actor AS uuid) IS NULL OR actor_id = :actor)"
                + " AND (CAST(:from AS timestamptz) IS NULL OR occurred_at >= :from)"
                + " AND (CAST(:to AS timestamptz) IS NULL OR occurred_at <= :to)"
                + " AND (CAST(:before AS timestamptz) IS NULL OR"
                + " (occurred_at, id) < (CAST(:before AS timestamptz), CAST(:beforeId AS bigint)))"
                + " ORDER BY occurred_at DESC, id DESC LIMIT :limit")
        .param("aqueduct", aqueductId)
        .param("action", filter.action(), Types.VARCHAR)
        .param("entityType", filter.entityType(), Types.VARCHAR)
        .param("actor", filter.actorId(), Types.OTHER)
        .param("from", SqlTime.offset(filter.from()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(filter.to()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("before", SqlTime.offset(before), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("beforeId", beforeId, Types.BIGINT)
        .param("limit", limit)
        .query(this::view)
        .list();
  }

  private AuditLogView view(ResultSet rs, int row) throws SQLException {
    return new AuditLogView(
        rs.getLong("id"),
        SqlTime.instant(rs, "occurred_at"),
        rs.getString("actor_type"),
        rs.getObject("actor_id", UUID.class),
        rs.getString("action"),
        rs.getString("entity_type"),
        rs.getString("entity_id"),
        state(rs.getString("before_state")),
        state(rs.getString("after_state")),
        rs.getString("prev_hash"),
        rs.getString("row_hash"));
  }

  private Map<String, Object> state(String text) {
    return text == null ? null : json.readValue(text, STATE);
  }
}
