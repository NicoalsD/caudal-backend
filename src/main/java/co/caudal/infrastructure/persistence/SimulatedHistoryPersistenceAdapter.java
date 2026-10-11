package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.SimulatedHistoryPort;
import co.caudal.application.simulation.IncidentRow;
import co.caudal.application.simulation.ShiftExecutionRow;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Writes imported shift executions and incidents. Row level security limits the aqueduct. */
@Component
public class SimulatedHistoryPersistenceAdapter implements SimulatedHistoryPort {

  private static final String SOURCE_IMPORT = "IMPORT";
  private static final String REPORTED = "REPORTED";

  private final JdbcClient jdbc;

  SimulatedHistoryPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean scheduleItemExists(UUID scheduleItemId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM ops.schedule_items WHERE id = :id)")
        .param("id", scheduleItemId)
        .query(Boolean.class)
        .single();
  }

  @Override
  public void insertShiftExecution(
      UUID aqueductId, ShiftExecutionRow row, UUID importBatchId, UUID recordedBy) {
    jdbc.sql(
            "INSERT INTO ops.shift_executions (aqueduct_id, schedule_item_id, status,"
                + " actual_start, actual_end, note, import_batch_id, recorded_by) VALUES"
                + " (:aqueduct, :item, :status, :start, :end, :note, :import, :by)")
        .param("aqueduct", aqueductId)
        .param("item", row.scheduleItemId())
        .param("status", row.status())
        .param("start", SqlTime.offset(row.actualStart()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("end", SqlTime.offset(row.actualEnd()), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("note", row.note(), Types.VARCHAR)
        .param("import", importBatchId)
        .param("by", recordedBy)
        .update();
  }

  @Override
  public void insertIncident(
      UUID aqueductId,
      IncidentRow row,
      String trackingCodeHash,
      UUID importBatchId,
      UUID importedBy) {
    UUID incident =
        jdbc.sql(
                "INSERT INTO ops.incidents (aqueduct_id, source, category_code, sector_id,"
                    + " description, location_hint, tracking_code_hash, import_batch_id,"
                    + " reported_at) VALUES (:aqueduct, :source, :category, :sector,"
                    + " :description, :hint, :hash, :import, :at) RETURNING id")
            .param("aqueduct", aqueductId)
            .param("source", SOURCE_IMPORT)
            .param("category", row.categoryCode())
            .param("sector", row.sectorId(), Types.OTHER)
            .param("description", row.description().strip())
            .param("hint", row.locationHint(), Types.VARCHAR)
            .param("hash", trackingCodeHash)
            .param("import", importBatchId)
            .param("at", SqlTime.offset(row.reportedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
            .query(UUID.class)
            .single();
    jdbc.sql(
            "INSERT INTO ops.incident_status_history (aqueduct_id, incident_id, to_status,"
                + " changed_by, changed_at) VALUES (:aqueduct, :incident, :status, :by, :at)")
        .param("aqueduct", aqueductId)
        .param("incident", incident)
        .param("status", REPORTED)
        .param("by", importedBy)
        .param("at", SqlTime.offset(row.reportedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }
}
