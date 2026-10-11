package co.caudal.infrastructure.persistence;

import co.caudal.application.anomaly.AnomalyView;
import co.caudal.application.port.out.AnomalyPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes {@code ops.anomalies}. Row level security limits the aqueduct. */
@Component
public class AnomalyPersistenceAdapter implements AnomalyPort {

  private static final TypeReference<Map<String, Object>> EVIDENCE = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AnomalyPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public int countOpen() {
    return jdbc.sql("SELECT count(*) FROM ops.anomalies WHERE status = 'OPEN'")
        .query(Integer.class)
        .single();
  }

  @Override
  public boolean openExists(String kind, UUID tankId) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM ops.anomalies WHERE status = 'OPEN' AND kind = :kind"
                + " AND evidence->>'tank_id' = :tank)")
        .param("kind", kind)
        .param("tank", tankId.toString())
        .query(Boolean.class)
        .single();
  }

  @Override
  public UUID open(UUID aqueductId, String kind, Map<String, Object> evidence) {
    return jdbc.sql(
            "INSERT INTO ops.anomalies (aqueduct_id, kind, evidence) VALUES (:aqueduct, :kind,"
                + " CAST(:evidence AS jsonb)) RETURNING id")
        .param("aqueduct", aqueductId)
        .param("kind", kind)
        .param("evidence", json.writeValueAsString(evidence))
        .query(UUID.class)
        .single();
  }

  private static final String COLUMNS =
      "SELECT id, kind, detected_at, status, evidence, resolved_by, resolved_at,"
          + " resolution_note, related_incident_id FROM ops.anomalies";

  @Override
  public Optional<AnomalyView> find(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = :id").param("id", id).query(this::view).optional();
  }

  @Override
  public List<AnomalyView> list(String status, String kind, UUID beforeId, int limit) {
    return jdbc.sql(
            COLUMNS
                + " WHERE (CAST(:status AS varchar) IS NULL OR status = :status)"
                + " AND (CAST(:kind AS varchar) IS NULL OR kind = :kind)"
                + " AND (CAST(:before AS uuid) IS NULL OR id < :before)"
                + " ORDER BY id DESC LIMIT :limit")
        .param("status", status, Types.VARCHAR)
        .param("kind", kind, Types.VARCHAR)
        .param("before", beforeId, Types.OTHER)
        .param("limit", limit)
        .query(this::view)
        .list();
  }

  @Override
  public void resolve(
      UUID id,
      String status,
      UUID resolvedBy,
      Instant resolvedAt,
      String note,
      UUID relatedIncidentId) {
    jdbc.sql(
            "UPDATE ops.anomalies SET status = :status, resolved_by = :by, resolved_at = :at,"
                + " resolution_note = :note, related_incident_id = :incident"
                + " WHERE id = :id AND status = 'OPEN'")
        .param("status", status)
        .param("by", resolvedBy)
        .param("at", SqlTime.offset(resolvedAt), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("note", note, Types.VARCHAR)
        .param("incident", relatedIncidentId, Types.OTHER)
        .param("id", id)
        .update();
  }

  @Override
  public boolean incidentExists(UUID incidentId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM ops.incidents WHERE id = :id)")
        .param("id", incidentId)
        .query(Boolean.class)
        .single();
  }

  private AnomalyView view(ResultSet rs, int row) throws SQLException {
    return new AnomalyView(
        rs.getObject("id", UUID.class),
        rs.getString("kind"),
        SqlTime.instant(rs, "detected_at"),
        rs.getString("status"),
        json.readValue(rs.getString("evidence"), EVIDENCE),
        rs.getObject("resolved_by", UUID.class),
        SqlTime.instant(rs, "resolved_at"),
        rs.getString("resolution_note"),
        rs.getObject("related_incident_id", UUID.class));
  }
}
