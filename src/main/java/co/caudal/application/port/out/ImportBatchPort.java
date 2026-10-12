package co.caudal.application.port.out;

import co.caudal.application.simulation.ImportBatchRecord;
import java.util.UUID;

/** Stores the imports of simulated history ({@code sim.import_batches}, append only). */
public interface ImportBatchPort {

  /**
   * Tells whether a file was already imported into the aqueduct of the session.
   *
   * @param fileSha256 SHA-256 of the file
   * @return true if an import has it
   */
  boolean fileImported(String fileSha256);

  /**
   * Stores an import with its counts; it never changes afterwards.
   *
   * @param batch the import
   * @return its id
   */
  UUID insert(ImportBatchRecord batch);
}
