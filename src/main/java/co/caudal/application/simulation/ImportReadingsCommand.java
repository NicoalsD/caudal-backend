package co.caudal.application.simulation;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import co.caudal.application.reading.ReadingInput;
import java.util.List;

/**
 * Imports simulated readings into a demo aqueduct (docs/API.md, section 17.1; HU-38).
 *
 * @param request what the import declares
 * @param rows the readings
 */
public record ImportReadingsCommand(ImportRequest request, List<ReadingInput> rows)
    implements AuditableCommand<ImportResult> {

  /** Copies the rows. */
  public ImportReadingsCommand {
    rows = List.copyOf(rows);
  }

  @Override
  public Caller caller() {
    return request.caller();
  }

  @Override
  public String auditEntityType() {
    return "import_batch";
  }

  @Override
  public String auditEntityId(ImportResult result) {
    return result.importBatchId().toString();
  }
}
