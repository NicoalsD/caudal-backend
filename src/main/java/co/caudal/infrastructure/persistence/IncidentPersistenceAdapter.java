package co.caudal.infrastructure.persistence;

import co.caudal.application.incident.IncidentFilter;
import co.caudal.application.incident.IncidentView;
import co.caudal.application.incident.NewIncident;
import co.caudal.application.incident.TrackedReport;
import co.caudal.application.port.out.IncidentPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Stores incidents and their history. Row level security limits the aqueduct, except for the public
 * lookup by tracking code, which goes through {@code ops.find_public_report}.
 */
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

  @Override
  public Optional<TrackedReport> findByTrackingHash(String trackingCodeHash) {
    return jdbc.sql(
            "SELECT aqueduct_id, status, category_code, reported_at, updated_at"
                + " FROM ops.find_public_report(:hash)")
        .param("hash", trackingCodeHash)
        .query(
            (rs, row) ->
                new TrackedReport(
                    rs.getObject("aqueduct_id", UUID.class),
                    rs.getString("status"),
                    rs.getString("category_code"),
                    SqlTime.instant(rs, "reported_at"),
                    SqlTime.instant(rs, "updated_at")))
        .optional();
  }

  private static final String COLUMNS =
      "SELECT id, source, category_code, sector_id, description, location_hint, status,"
          + " reported_at FROM ops.incidents";

  @Override
  public List<IncidentView> list(IncidentFilter filter, Instant before, UUID beforeId, int limit) {
    return jdbc.sql(
            COLUMNS
                + " WHERE (CAST(:status AS varchar) IS NULL OR status = :status)"
                + " AND (CAST(:source AS varchar) IS NULL OR source = :source)"
                + " AND (CAST(:sector AS uuid) IS NULL OR sector_id = :sector)"
                + " AND (CAST(:from AS timestamptz) IS NULL OR reported_at >= :from)"
                + " AND (CAST(:to AS timestamptz) IS NULL OR reported_at <= :to)"
                + " AND (CAST(:before AS timestamptz) IS NULL OR"
                + " (reported_at, id) < (CAST(:before AS timestamptz), CAST(:beforeId AS uuid)))"
                + " ORDER BY reported_at DESC, id DESC LIMIT :limit")
        .param("status", filter.status(), Types.VARCHAR)
        .param("source", filter.source(), Types.VARCHAR)
        .param("sector", filter.sectorId(), Types.OTHER)
        .param("from", SqlTime.offset(filter.from()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("to", SqlTime.offset(filter.to()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("before", SqlTime.offset(before), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("beforeId", beforeId, Types.OTHER)
        .param("limit", limit)
        .query(IncidentPersistenceAdapter::view)
        .list();
  }

  private static IncidentView view(ResultSet rs, int row) throws SQLException {
    return new IncidentView(
        rs.getObject("id", UUID.class),
        rs.getString("source"),
        rs.getString("category_code"),
        rs.getObject("sector_id", UUID.class),
        rs.getString("description"),
        rs.getString("location_hint"),
        rs.getString("status"),
        SqlTime.instant(rs, "reported_at"));
  }
}
