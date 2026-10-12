package co.caudal.infrastructure.persistence;

import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.application.simulation.ImportBatchRecord;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads and writes {@code sim.import_batches}. Row level security limits the aqueduct. */
@Component
public class ImportBatchPersistenceAdapter implements ImportBatchPort {

  private final JdbcClient jdbc;

  ImportBatchPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean fileImported(String fileSha256) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM sim.import_batches WHERE file_sha256 = :sha)")
        .param("sha", fileSha256)
        .query(Boolean.class)
        .single();
  }

  @Override
  public UUID insert(ImportBatchRecord batch) {
    return jdbc.sql(
            "INSERT INTO sim.import_batches (aqueduct_id, kind, scenario_name, seed,"
                + " simulator_version, rows_received, rows_accepted, rows_rejected, file_sha256,"
                + " imported_by) VALUES (:aqueduct, :kind, :scenario, :seed, :version, :received,"
                + " :accepted, :rejected, :sha, :by) RETURNING id")
        .param("aqueduct", batch.aqueductId())
        .param("kind", batch.kind())
        .param("scenario", batch.scenarioName())
        .param("seed", batch.seed())
        .param("version", batch.simulatorVersion())
        .param("received", batch.rowsReceived())
        .param("accepted", batch.rowsAccepted())
        .param("rejected", batch.rowsRejected())
        .param("sha", batch.fileSha256())
        .param("by", batch.importedBy())
        .query(UUID.class)
        .single();
  }
}
