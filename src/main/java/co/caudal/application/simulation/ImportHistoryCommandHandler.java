package co.caudal.application.simulation;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.SimulatedHistoryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.incident.TrackingCode;
import co.caudal.shared.FieldLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Imports simulated shift executions or incidents into a demo aqueduct (docs/Datos-simulados.md;
 * docs/API.md, sections 17.2 and 17.3). Each row is checked on its own and a refused row does not
 * stop the import; the import is written once in {@code sim.import_batches}, with its counts,
 * before its rows. An imported incident gets its own tracking code, of which only the hash is kept.
 *
 * @pattern P12 Chain of Responsibility
 * @pattern P13 Command
 */
public class ImportHistoryCommandHandler
    implements CommandHandler<ImportHistoryCommand, ImportResult> {

  private static final String DAMAGE_CATEGORY = "DAMAGE_CATEGORY";
  private static final Set<String> EXECUTION_STATUSES =
      Set.of("COMPLETED", "PARTIAL", "NOT_EXECUTED");
  private static final String INVALID = "VALIDATION_ERROR";
  private static final String NOT_FOUND = "NOT_FOUND";

  private final DemoImportGuard guard;
  private final ImportBatchPort imports;
  private final SimulatedHistoryPort history;
  private final CatalogPort catalogs;
  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param guard lets imports in only into demo aqueducts
   * @param imports imports of simulated history
   * @param history shift executions and incidents
   * @param catalogs option lists
   * @param network sectors
   * @param unitOfWork transaction boundary
   */
  public ImportHistoryCommandHandler(
      DemoImportGuard guard,
      ImportBatchPort imports,
      SimulatedHistoryPort history,
      CatalogPort catalogs,
      NetworkPort network,
      UnitOfWorkPort unitOfWork) {
    this.guard = guard;
    this.imports = imports;
    this.history = history;
    this.catalogs = catalogs;
    this.network = network;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ImportResult handle(ImportHistoryCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> importRows(command));
  }

  private ImportResult importRows(ImportHistoryCommand command) {
    guard.check(command.request());
    boolean incidents = !command.incidents().isEmpty();
    int total = incidents ? command.incidents().size() : command.executions().size();
    List<ImportRejection> rejections = new ArrayList<>();
    List<Integer> valid = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      String problem =
          incidents
              ? problem(command.incidents().get(i), command.caller().aqueductId())
              : problem(command.executions().get(i));
      if (problem == null) {
        valid.add(i);
      } else {
        rejections.add(new ImportRejection(i + 1, problem));
      }
    }
    ImportRequest request = command.request();
    UUID importId =
        imports.insert(
            new ImportBatchRecord(
                request.aqueductId(),
                command.kind(),
                request.scenarioName(),
                request.seed(),
                request.simulatorVersion(),
                request.fileSha256(),
                total,
                valid.size(),
                rejections.size(),
                command.caller().userId()));
    UUID aqueduct = command.caller().aqueductId();
    UUID importer = command.caller().userId();
    for (int i : valid) {
      if (incidents) {
        history.insertIncident(
            aqueduct,
            command.incidents().get(i),
            TrackingCode.generate().hash(),
            importId,
            importer);
      } else {
        history.insertShiftExecution(aqueduct, command.executions().get(i), importId, importer);
      }
    }
    return new ImportResult(importId, total, valid.size(), rejections.size(), rejections);
  }

  private String problem(ShiftExecutionRow row) {
    if (row.scheduleItemId() == null || !EXECUTION_STATUSES.contains(row.status())) {
      return INVALID;
    }
    if (row.actualStart() != null
        && row.actualEnd() != null
        && !row.actualEnd().isAfter(row.actualStart())) {
      return INVALID;
    }
    if (row.note() != null && row.note().length() > FieldLimits.NOTE_MAX) {
      return INVALID;
    }
    return history.scheduleItemExists(row.scheduleItemId()) ? null : NOT_FOUND;
  }

  private String problem(IncidentRow row, UUID aqueduct) {
    String description = row.description() == null ? "" : row.description().strip();
    if (description.length() < FieldLimits.INCIDENT_DESCRIPTION_MIN
        || description.length() > FieldLimits.INCIDENT_DESCRIPTION_MAX
        || row.reportedAt() == null
        || (row.locationHint() != null
            && row.locationHint().length() > FieldLimits.LOCATION_HINT_MAX)) {
      return INVALID;
    }
    if (!catalogs.isActiveCode(DAMAGE_CATEGORY, row.categoryCode(), aqueduct)) {
      return INVALID;
    }
    if (row.sectorId() != null && network.sector(row.sectorId()).isEmpty()) {
      return NOT_FOUND;
    }
    return null;
  }

  @Override
  public Class<ImportHistoryCommand> commandType() {
    return ImportHistoryCommand.class;
  }
}
