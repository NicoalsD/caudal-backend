package co.caudal.application.reading;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.RuleSetView;
import co.caudal.domain.reading.ReadingContent;
import co.caudal.domain.reading.validation.ReadingCandidate;
import co.caudal.domain.reading.validation.ReadingCandidate.ReadingLimits;
import co.caudal.domain.reading.validation.ReadingHistory;
import co.caudal.domain.reading.validation.ReadingIssue;
import co.caudal.domain.reading.validation.ReadingValidationChain;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates and stores a reading (docs/Reglas-de-negocio.md, section 8). A reading that breaks the
 * gauge range, the date rules is refused with 422 and not stored. A repeated value or a jump that
 * is too fast is stored as {@code FLAGGED} with its issue. The same id with the same content is a
 * harmless retry.
 *
 * @pattern P12 Chain of Responsibility
 * @pattern P13 Command
 */
public class CreateReadingCommandHandler
    implements CommandHandler<CreateReadingCommand, RecordedReading> {

  private static final String WATER_APPEARANCE = "WATER_APPEARANCE";
  private static final String SOURCE_MANUAL = "MANUAL_APP";
  private static final String ACCEPTED = "ACCEPTED";
  private static final String FLAGGED = "FLAGGED";

  private final NetworkPort network;
  private final RuleSetPort ruleSets;
  private final ReadingPort readings;
  private final CatalogPort catalogs;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param network tanks
   * @param ruleSets rules
   * @param readings readings
   * @param catalogs option lists
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public CreateReadingCommandHandler(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      CatalogPort catalogs,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.network = network;
    this.ruleSets = ruleSets;
    this.readings = readings;
    this.catalogs = catalogs;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public RecordedReading handle(CreateReadingCommand command) {
    String note = normalizeNote(command.note());
    return unitOfWork.executeAs(command.caller().scope(), () -> record(command, note));
  }

  private RecordedReading record(CreateReadingCommand command, String note) {
    TankView tank =
        network
            .tank(command.tankId())
            .filter(TankView::active)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Optional<ReadingView> existing = readings.findById(command.id());
    if (existing.isPresent()) {
      return replay(existing.get(), command, note);
    }
    RuleSetView rules =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    BigDecimal value = command.gaugeValue();
    Instant now = clock.instant();
    List<ReadingIssue> found =
        ReadingValidationChain.run(
            ReadingValidationChain.standard(history(command, tank)),
            candidate(command, tank, rules, now));
    Instant observedAt = command.observedAt();
    if (!catalogs.isActiveCode(
        WATER_APPEARANCE, command.waterAppearanceCode(), command.caller().aqueductId())) {
      throw new FieldValidationException("water_appearance_code", "FIELD_VALUE_NOT_ALLOWED");
    }
    List<IssueView> issues = found.stream().map(CreateReadingCommandHandler::view).toList();
    ReadingView reading =
        new ReadingView(
            command.id(),
            tank.id(),
            value,
            command.waterAppearanceCode(),
            command.damageNoticed(),
            note,
            observedAt,
            now,
            SOURCE_MANUAL,
            issues.isEmpty() ? ACCEPTED : FLAGGED,
            rules.id());
    if (!readings.insert(command.caller().aqueductId(), reading, command.caller().userId())) {
      throw new DomainException(ErrorCode.DUPLICATE_READING);
    }
    for (IssueView issue : issues) {
      readings.insertIssue(command.caller().aqueductId(), reading.id(), issue);
    }
    return new RecordedReading(readings.findById(reading.id()).orElse(reading), issues, false);
  }

  private ReadingHistory history(CreateReadingCommand command, TankView tank) {
    return new ReadingHistory() {
      @Override
      public Optional<UUID> duplicateOf(BigDecimal value, Instant observedAt, int windowMinutes) {
        return readings.findDuplicate(tank.id(), value, observedAt, windowMinutes, command.id());
      }

      @Override
      public Optional<LevelAt> previous(Instant observedAt) {
        return readings
            .previousEffective(tank.id(), observedAt)
            .map(level -> new LevelAt(level.value(), level.observedAt()));
      }
    };
  }

  private static IssueView view(ReadingIssue issue) {
    return new IssueView(issue.code(), issue.severity(), issue.details());
  }

  private static ReadingCandidate candidate(
      CreateReadingCommand command, TankView tank, RuleSetView rules, Instant now) {
    return new ReadingCandidate(
        command.gaugeValue(),
        command.observedAt(),
        now,
        tank.gaugeMin(),
        tank.gaugeMax(),
        new ReadingLimits(
            rules.maxBackdateDays(), rules.duplicateWindowMinutes(), rules.maxLevelChangePerHour()),
        false);
  }

  /**
   * The same client id again: the same content is a retry that returns the stored reading, other
   * content is a conflict.
   */
  private RecordedReading replay(ReadingView existing, CreateReadingCommand command, String note) {
    ReadingContent stored =
        new ReadingContent(
            existing.tankId(),
            existing.gaugeValue(),
            existing.waterAppearanceCode(),
            existing.damageNoticed(),
            existing.note(),
            existing.observedAt());
    ReadingContent incoming =
        new ReadingContent(
            command.tankId(),
            command.gaugeValue(),
            command.waterAppearanceCode(),
            command.damageNoticed(),
            note,
            command.observedAt());
    if (!stored.sameAs(incoming)) {
      throw new DomainException(ErrorCode.DUPLICATE_READING);
    }
    return new RecordedReading(existing, readings.issues(existing.id()), true);
  }

  private static String normalizeNote(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String note = raw.strip().replace("\r\n", "\n");
    if (note.length() > FieldLimits.NOTE_MAX) {
      throw new FieldValidationException("note", "FIELD_TOO_LONG");
    }
    if (note.lines().count() > FieldLimits.NOTE_MAX_LINES) {
      throw new FieldValidationException("note", "FIELD_TOO_MANY_LINES");
    }
    return note;
  }

  @Override
  public Class<CreateReadingCommand> commandType() {
    return CreateReadingCommand.class;
  }
}
