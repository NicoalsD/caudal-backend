package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.AnomalyPort;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes {@code ops.anomalies}. Row level security limits the aqueduct. */
@Component
public class AnomalyPersistenceAdapter implements AnomalyPort {

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
}
