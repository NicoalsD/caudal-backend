package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.DataAccessLogPort;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Writes reads of sensitive documents. Row level security limits the aqueduct. */
@Component
public class DataAccessLogPersistenceAdapter implements DataAccessLogPort {

  private final JdbcClient jdbc;

  DataAccessLogPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(
      UUID aqueductId, UUID userId, String resource, String resourceId, String ipHmac) {
    jdbc.sql(
            "INSERT INTO audit.data_access_log (aqueduct_id, user_id, resource, resource_id,"
                + " ip_hmac) VALUES (:aqueduct, :user, :resource, :id, :ip)")
        .param("aqueduct", aqueductId)
        .param("user", userId)
        .param("resource", resource)
        .param("id", resourceId)
        .param("ip", ipHmac, Types.VARCHAR)
        .update();
  }

  @Override
  public void recordSummary(UUID aqueductId, UUID userId, UUID summaryShareId, String ipHmac) {
    jdbc.sql(
            "INSERT INTO audit.data_access_log (aqueduct_id, user_id, resource, resource_id,"
                + " summary_share_id, ip_hmac) VALUES (:aqueduct, :user, 'SUMMARY', :id, :share,"
                + " :ip)")
        .param("aqueduct", aqueductId)
        .param("user", userId)
        .param("id", summaryShareId.toString())
        .param("share", summaryShareId)
        .param("ip", ipHmac, Types.VARCHAR)
        .update();
  }
}
