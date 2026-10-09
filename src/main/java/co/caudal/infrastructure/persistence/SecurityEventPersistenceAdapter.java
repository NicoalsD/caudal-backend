package co.caudal.infrastructure.persistence;

import co.caudal.api.filter.RequestIdFilter;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.domain.security.SecurityEvent;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code iam.security_events}. The insert joins the transaction in progress, so the event is
 * saved or rolled back together with the change that caused it. The details are already redacted by
 * the caller.
 */
@Component
public class SecurityEventPersistenceAdapter implements SecurityEventPort {

  private static final String INSERT =
      "INSERT INTO iam.security_events (aqueduct_id, type, severity, actor_user_id, ip_hmac,"
          + " request_id, details) VALUES (:aqueductId, :type, :severity, :actorUserId, :ipHmac,"
          + " :requestId, CAST(:details AS jsonb))";

  private final JdbcClient jdbc;
  private final JsonMapper json;

  SecurityEventPersistenceAdapter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void record(SecurityEvent event) {
    jdbc.sql(INSERT)
        .param("aqueductId", event.aqueductId())
        .param("type", event.type().name())
        .param("severity", event.severity().name())
        .param("actorUserId", event.actorUserId())
        .param("ipHmac", event.ipHmac())
        .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
        .param("details", json.writeValueAsString(event.details()))
        .update();
  }
}
