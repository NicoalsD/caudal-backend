package co.caudal.application.simulation;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.util.List;

/**
 * Imports simulated shift executions or incidents into a demo aqueduct (docs/API.md, sections 17.2
 * and 17.3; HU-38).
 *
 * @param request what the import declares
 * @param executions the shift executions, empty for an import of incidents
 * @param incidents the incidents, empty for an import of shift executions
 */
public record ImportHistoryCommand(
    ImportRequest request, List<ShiftExecutionRow> executions, List<IncidentRow> incidents)
    implements AuditableCommand<ImportResult> {

  /** Copies the rows; an import carries one kind of row. */
  public ImportHistoryCommand {
    executions = List.copyOf(executions);
    incidents = List.copyOf(incidents);
    if (!executions.isEmpty() && !incidents.isEmpty()) {
      throw new IllegalArgumentException("One kind of row per import");
    }
  }

  /**
   * The kind stored in {@code sim.import_batches}.
   *
   * @return {@code SHIFT_EXECUTIONS} or {@code INCIDENTS}
   */
  public String kind() {
    return incidents.isEmpty() ? "SHIFT_EXECUTIONS" : "INCIDENTS";
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
