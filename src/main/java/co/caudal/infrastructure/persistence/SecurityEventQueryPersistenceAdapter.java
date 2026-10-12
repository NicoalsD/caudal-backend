package co.caudal.infrastructure.persistence;

import co.caudal.application.audit.SecurityEventFilter;
import co.caudal.application.audit.SecurityEventView;
import co.caudal.application.port.out.SecurityEventQueryPort;
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
 * Reads {@code iam.security_events}. This table has no row level security, so the query always
 * names the aqueduct and never selects the HMAC of the address.
 */
@Component
public class SecurityEventQueryPersistenceAdapter implements SecurityEventQueryPort {

  private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  SecurityEventQueryPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public List<SecurityEventView> list(
      UUID aqueductId, SecurityEventFilter filter, Instant before, Long beforeId, int limit) {
    return jdbc.sql(
            "SELECT id, type, severity, actor_user_id, device_id, request_id, created_at,"
                + " details::text AS details FROM iam.security_events WHERE aqueduct_id = :aqueduct"
                + " AND (CAST(:type AS varchar) IS NULL OR type = :type)"
                + " AND (CAST(:severity AS varchar) IS NULL OR severity = :severity)"
                + " AND (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)"
                + " AND (CAST(:to AS timestamptz) IS NULL OR created_at <= :to)"
                + " AND (CAST(:before AS timestamptz) IS NULL OR"
                + " (created_at, id) < (CAST(:before AS timestamptz), CAST(:beforeId AS bigint)))"
                + " ORDER BY created_at DESC, id DESC LIMIT :limit")
        .param("aqueduct", aqueductId)
        .param("type", filter.type(), Types.VARCHAR)
        .param("severity", filter.severity(), Types.VARCHAR)
        .param("from", SqlTime.offset(filter.from()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(filter.to()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("before", SqlTime.offset(before), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("beforeId", beforeId, Types.BIGINT)
        .param("limit", limit)
        .query(this::view)
        .list();
  }

  private SecurityEventView view(ResultSet rs, int row) throws SQLException {
    return new SecurityEventView(
        rs.getLong("id"),
        rs.getString("type"),
        rs.getString("severity"),
        rs.getObject("actor_user_id", UUID.class),
        rs.getObject("device_id", UUID.class),
        rs.getString("request_id"),
        SqlTime.instant(rs, "created_at"),
        json.readValue(rs.getString("details"), DETAILS));
  }
}
