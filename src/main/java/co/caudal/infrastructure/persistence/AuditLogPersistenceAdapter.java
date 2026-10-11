package co.caudal.infrastructure.persistence;

import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.command.AuditEntry;
import co.caudal.application.port.out.AuditLogPort;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inserts into {@code audit.audit_log}. The trigger {@code audit_log_chain} fills {@code prev_hash}
 * and {@code row_hash}; the request id comes from the logging context of the request.
 */
@Component
public class AuditLogPersistenceAdapter implements AuditLogPort {

  private static final String ACTOR_USER = "USER";

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AuditLogPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void append(AuditEntry entry) {
    jdbc.sql(
            "INSERT INTO audit.audit_log (aqueduct_id, actor_type, actor_id, action, entity_type,"
                + " entity_id, after_state, request_id, ip_hmac, prev_hash, row_hash) VALUES"
                + " (:aqueduct, :actorType, :actor, :action, :entityType, :entityId,"
                + " CAST(:after AS jsonb), :requestId, :ip, '', '')")
        .param("aqueduct", entry.aqueductId())
        .param("actorType", ACTOR_USER)
        .param("actor", entry.actorId())
        .param("action", entry.action())
        .param("entityType", entry.entityType())
        .param("entityId", entry.entityId())
        .param(
            "after",
            entry.afterState() == null ? null : json.writeValueAsString(entry.afterState()),
            java.sql.Types.VARCHAR)
        .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
        .param("ip", entry.ipHmac())
        .update();
  }
}
