package co.caudal.application.simulation;

import java.util.List;
import java.util.UUID;

/**
 * The result of an import of simulated history (docs/API.md, section 17).
 *
 * @param importBatchId the import
 * @param rowsReceived rows it had
 * @param rowsAccepted rows stored
 * @param rowsRejected rows refused
 * @param rejections why each refused row was refused
 */
public record ImportResult(
    UUID importBatchId,
    int rowsReceived,
    int rowsAccepted,
    int rowsRejected,
    List<ImportRejection> rejections) {

  /** Copies the rejections. */
  public ImportResult {
    rejections = List.copyOf(rejections);
  }
}
