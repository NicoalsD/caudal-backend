package co.caudal.infrastructure.persistence;

import co.caudal.application.incident.NewIncident;
import co.caudal.application.port.out.IncidentPort;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stores incidents and their history. Row level security limits the aqueduct. */
@Component
public class IncidentPersistenceAdapter implements IncidentPort {

  private static final String REPORTED = "REPORTED";

  private final JdbcClient jdbc;

  IncidentPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UUID insert(UUID aqueductId, NewIncident incident) {
    UUID id =
        jdbc.sql(
                "INSERT INTO ops.incidents (aqueduct_id, source, category_code, sector_id,"
                    + " description, location_hint, status, tracking_code_hash, reporter_ip_hmac)"
                    + " VALUES (:aqueduct, :source, :category, :sector, :description, :location,"
                    + " :status, :hash, :ip) RETURNING id")
            .param("aqueduct", aqueductId)
            .param("source", incident.source())
            .param("category", incident.categoryCode())
            .param("sector", incident.sectorId(), Types.OTHER)
            .param("description", incident.description())
            .param("location", incident.locationHint(), Types.VARCHAR)
            .param("status", REPORTED)
            .param("hash", incident.trackingCodeHash())
            .param("ip", incident.reporterIpHmac(), Types.VARCHAR)
            .query(UUID.class)
            .single();
    jdbc.sql(
            "INSERT INTO ops.incident_status_history (aqueduct_id, incident_id, from_status,"
                + " to_status) VALUES (:aqueduct, :incident, NULL, :status)")
        .param("aqueduct", aqueductId)
        .param("incident", id)
        .param("status", REPORTED)
        .update();
    return id;
  }
}
