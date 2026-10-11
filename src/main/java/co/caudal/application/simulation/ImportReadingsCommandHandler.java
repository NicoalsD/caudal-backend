package co.caudal.application.simulation;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.ImportBatchPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.ReadingInput;
import co.caudal.application.reading.ReadingOrigin;
import co.caudal.application.reading.ReadingRecorder;
import co.caudal.application.rules.RuleSetView;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Imports simulated readings into a demo aqueduct (docs/Datos-simulados.md; docs/API.md, section
 * 17.1). The rows go through the same checks as a reading of the app, except the age: simulated
 * history may be older than {@code max_backdate_days}. A refused row does not stop the import. The
 * import is written once in {@code sim.import_batches}, with its counts, before its readings, which
 * are stored in time order with {@code source = IMPORT} and the id of the import.
 *
 * @pattern P12 Chain of Responsibility
 * @pattern P13 Command
 */
public class ImportReadingsCommandHandler
    implements CommandHandler<ImportReadingsCommand, ImportResult> {

  private static final String KIND = "READINGS";

  private final DemoImportGuard guard;
  private final RuleSetPort ruleSets;
  private final ImportBatchPort imports;
  private final ReadingRecorder recorder;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param guard lets imports in only into demo aqueducts
   * @param ruleSets rules
   * @param imports imports of simulated history
   * @param recorder judges and stores readings
   * @param unitOfWork transaction boundary
   */
  public ImportReadingsCommandHandler(
      DemoImportGuard guard,
      RuleSetPort ruleSets,
      ImportBatchPort imports,
      ReadingRecorder recorder,
      UnitOfWorkPort unitOfWork) {
    this.guard = guard;
    this.ruleSets = ruleSets;
    this.imports = imports;
    this.recorder = recorder;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ImportResult handle(ImportReadingsCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> importRows(command));
  }

  private ImportResult importRows(ImportReadingsCommand command) {
    ImportRequest request = command.request();
    guard.check(request);
    RuleSetView rules =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    List<ImportRejection> rejections = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    Set<Integer> valid = new HashSet<>();
    for (int i = 0; i < command.rows().size(); i++) {
      ReadingInput row = command.rows().get(i);
      if (!seen.add(row.id())) {
        rejections.add(new ImportRejection(i + 1, ErrorCode.DUPLICATE_READING.name()));
        continue;
      }
      try {
        recorder.judge(command.caller(), row, rules, true);
        valid.add(i);
      } catch (DomainException refused) {
        rejections.add(new ImportRejection(i + 1, refused.code().name()));
      }
    }
    UUID importId =
        imports.insert(
            new ImportBatchRecord(
                request.aqueductId(),
                KIND,
                request.scenarioName(),
                request.seed(),
                request.simulatorVersion(),
                request.fileSha256(),
                command.rows().size(),
                valid.size(),
                rejections.size(),
                command.caller().userId()));
    List<Integer> inTimeOrder =
        IntStream.range(0, command.rows().size())
            .filter(valid::contains)
            .boxed()
            .sorted(
                Comparator.comparing(
                    i -> command.rows().get(i).observedAt(),
                    Comparator.nullsFirst(Comparator.<Instant>naturalOrder())))
            .toList();
    for (int i : inTimeOrder) {
      ReadingInput row = command.rows().get(i);
      recorder.store(
          command.caller(),
          recorder.judge(command.caller(), row, rules, true),
          rules,
          ReadingOrigin.imported(importId));
    }
    return new ImportResult(
        importId, command.rows().size(), valid.size(), rejections.size(), rejections);
  }

  @Override
  public Class<ImportReadingsCommand> commandType() {
    return ImportReadingsCommand.class;
  }
}
