package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.SyncBatchPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.ReadingRecorder.Verdict;
import co.caudal.application.rules.RuleSetView;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Processes the offline queue of the app (docs/API.md, section 7.2; HU-10). Each reading is judged
 * on its own with the {@link ReadingRecorder}: a refused one does not stop the batch, its result
 * says why. Readings are stored in the order they were observed, so the checks against earlier
 * levels see them in time order, and the results come back in the order they were sent.
 *
 * <p>The batch is idempotent by its id: {@code ops.sync_batches} is append only and is written
 * once, with its counts, before its readings. Sending the same batch again answers the same results
 * without storing anything; a batch id already used with other readings is a conflict. The ids
 * inside one batch must be different.
 *
 * @pattern P13 Command
 */
public class CreateReadingBatchCommandHandler
    implements CommandHandler<CreateReadingBatchCommand, BatchResult> {

  private static final String ACCEPTED = "ACCEPTED";
  private static final String FLAGGED = "FLAGGED";

  private final RuleSetPort ruleSets;
  private final SyncBatchPort batches;
  private final ReadingRecorder recorder;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param ruleSets rules
   * @param batches offline batches
   * @param recorder judges and stores readings
   * @param unitOfWork transaction boundary
   */
  public CreateReadingBatchCommandHandler(
      RuleSetPort ruleSets,
      SyncBatchPort batches,
      ReadingRecorder recorder,
      UnitOfWorkPort unitOfWork) {
    this.ruleSets = ruleSets;
    this.batches = batches;
    this.recorder = recorder;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public BatchResult handle(CreateReadingBatchCommand command) {
    Set<UUID> ids = new HashSet<>();
    for (ReadingInput input : command.readings()) {
      if (!ids.add(input.id())) {
        throw new FieldValidationException("readings", "FIELD_VALUE_NOT_ALLOWED");
      }
    }
    return unitOfWork.executeAs(command.caller().scope(), () -> process(command));
  }

  private BatchResult process(CreateReadingBatchCommand command) {
    RuleSetView rules =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Optional<SyncBatchRecord> existing = batches.find(command.syncBatchId());
    if (existing.isPresent()) {
      return replay(command, existing.get(), rules);
    }
    Set<UUID> judged = new HashSet<>();
    Map<UUID, BatchItemResult> results = new HashMap<>();
    for (ReadingInput input : inObservedOrder(command.readings())) {
      try {
        recorder.judge(command.caller(), input, rules, false);
        judged.add(input.id());
      } catch (DomainException refused) {
        results.put(input.id(), rejected(input.id(), refused));
      }
    }
    int rejected = results.size();
    batches.insert(
        command.caller().aqueductId(),
        command.caller().userId(),
        new SyncBatchRecord(
            command.syncBatchId(),
            command.readings().size(),
            command.readings().size() - rejected,
            rejected));
    for (ReadingInput input : inObservedOrder(command.readings())) {
      if (judged.contains(input.id())) {
        results.put(input.id(), store(command.caller(), input, rules, command.syncBatchId()));
      }
    }
    return result(command, results, command.readings().size() - rejected, rejected, false);
  }

  /** Judges again with the history that now includes the readings stored before this one. */
  private BatchItemResult store(Caller caller, ReadingInput input, RuleSetView rules, UUID batch) {
    Verdict verdict = recorder.judge(caller, input, rules, false);
    RecordedReading recorded =
        recorder.store(caller, verdict, rules, ReadingOrigin.offlineBatch(batch));
    String status =
        recorded.replay()
            ? BatchItemResult.REPLAYED
            : recorded.issues().isEmpty() ? ACCEPTED : FLAGGED;
    return new BatchItemResult(input.id(), status, null, null);
  }

  private BatchResult replay(
      CreateReadingBatchCommand command, SyncBatchRecord stored, RuleSetView rules) {
    Set<UUID> storedIds = batches.readingIds(stored.id());
    if (stored.itemsReceived() != command.readings().size()) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "SYNC_BATCH_REUSED"));
    }
    Map<UUID, BatchItemResult> results = new HashMap<>();
    for (ReadingInput input : command.readings()) {
      BatchItemResult result;
      try {
        Verdict verdict = recorder.judge(command.caller(), input, rules, false);
        if (!verdict.replay() || !storedIds.contains(input.id())) {
          throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "SYNC_BATCH_REUSED"));
        }
        result = new BatchItemResult(input.id(), BatchItemResult.REPLAYED, null, null);
      } catch (DomainException refused) {
        if (refused.code() == ErrorCode.CONFLICT || refused.code() == ErrorCode.DUPLICATE_READING) {
          throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "SYNC_BATCH_REUSED"));
        }
        result = rejected(input.id(), refused);
      }
      results.put(input.id(), result);
    }
    return result(command, results, stored.itemsAccepted(), stored.itemsRejected(), true);
  }

  private static BatchItemResult rejected(UUID id, DomainException refused) {
    return new BatchItemResult(
        id, BatchItemResult.REJECTED, refused.code().name(), refused.details());
  }

  private static List<ReadingInput> inObservedOrder(List<ReadingInput> readings) {
    List<ReadingInput> ordered = new ArrayList<>(readings);
    ordered.sort(
        Comparator.comparing(
            ReadingInput::observedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
    return ordered;
  }

  private static BatchResult result(
      CreateReadingBatchCommand command,
      Map<UUID, BatchItemResult> results,
      int accepted,
      int rejected,
      boolean replay) {
    List<BatchItemResult> list = new ArrayList<>();
    for (ReadingInput input : command.readings()) {
      list.add(results.get(input.id()));
    }
    return new BatchResult(
        command.syncBatchId(), command.readings().size(), accepted, rejected, replay, list);
  }

  @Override
  public Class<CreateReadingBatchCommand> commandType() {
    return CreateReadingBatchCommand.class;
  }
}
