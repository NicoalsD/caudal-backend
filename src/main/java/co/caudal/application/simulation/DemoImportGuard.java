package co.caudal.application.simulation;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Lets simulated history in only where it belongs (docs/Datos-simulados.md; docs/API.md, section
 * 17): the aqueduct of the session, marked {@code is_demo}, at most {@link
 * FieldLimits#IMPORT_MAX_ROWS} rows, and a file never imported before. The database refuses an
 * import into a real aqueduct too ({@code sim.guard_demo_only}).
 */
public class DemoImportGuard {

  private final AqueductPort aqueducts;
  private final ImportBatchPort imports;

  /**
   * Creates the guard.
   *
   * @param aqueducts aqueducts, for the demo flag
   * @param imports earlier imports
   */
  public DemoImportGuard(AqueductPort aqueducts, ImportBatchPort imports) {
    this.aqueducts = aqueducts;
    this.imports = imports;
  }

  /**
   * Checks an import; it must run in a transaction scoped to the caller's aqueduct.
   *
   * @param request the import
   * @throws DomainException with {@code DEMO_ONLY} for another or a real aqueduct, {@code
   *     IMPORT_LIMIT_EXCEEDED} for too many rows or {@code CONFLICT} for a file already imported
   */
  public void check(ImportRequest request) {
    if (!request.aqueductId().equals(request.caller().aqueductId())) {
      throw new DomainException(ErrorCode.DEMO_ONLY);
    }
    boolean demo = aqueducts.findById(request.aqueductId()).map(AqueductRef::demo).orElse(false);
    if (!demo) {
      throw new DomainException(ErrorCode.DEMO_ONLY);
    }
    if (request.rowCount() > FieldLimits.IMPORT_MAX_ROWS) {
      throw new DomainException(
          ErrorCode.IMPORT_LIMIT_EXCEEDED, Map.of("max_rows", FieldLimits.IMPORT_MAX_ROWS));
    }
    if (imports.fileImported(request.fileSha256())) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "FILE_ALREADY_IMPORTED"));
    }
  }
}
