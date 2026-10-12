package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.CatalogPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
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
 * Judges and stores readings (docs/Reglas-de-negocio.md, section 8), the same way for a reading
 * sent alone, inside an offline batch or imported from the simulator. It must run inside a
 * transaction scoped to the caller's aqueduct.
 *
 * <p>A reading that breaks the range or the date rules is refused and not stored. A repeated value
 * or a jump that is too fast is stored as {@code FLAGGED} with its issue. The same id with the same
 * content is a harmless retry; with other content it is a conflict.
 *
 * @pattern P12 Chain of Responsibility
 */
public class ReadingRecorder {

  private static final String WATER_APPEARANCE = "WATER_APPEARANCE";
  private static final String ACCEPTED = "ACCEPTED";
  private static final String FLAGGED = "FLAGGED";

  private final NetworkPort network;
  private final ReadingPort readings;
  private final CatalogPort catalogs;
  private final Clock clock;

  /**
   * The verdict on a reading before it is stored.
   *
   * @param input the reading
   * @param tank its tank
   * @param replayOf the stored reading when this one is a retry, or null
   * @param issues the warnings, empty for a clean reading
   */
  public record Verdict(
      ReadingInput input, TankView tank, ReadingView replayOf, List<IssueView> issues) {

    /** Copies the issues. */
    public Verdict {
      issues = List.copyOf(issues);
    }

    /**
     * Tells whether the reading was already stored.
     *
     * @return true for a retry
     */
    public boolean replay() {
      return replayOf != null;
    }
  }

  /**
   * Creates the recorder.
   *
   * @param network tanks
   * @param readings readings
   * @param catalogs option lists
   * @param clock source of time
   */
  public ReadingRecorder(
      NetworkPort network, ReadingPort readings, CatalogPort catalogs, Clock clock) {
    this.network = network;
    this.readings = readings;
    this.catalogs = catalogs;
    this.clock = clock;
  }

  /**
   * Judges a reading without storing it.
   *
   * @param caller who sends it
   * @param input the reading
   * @param rules the active rules
   * @param ageExempt true for simulated history of a demo aqueduct
   * @return the verdict
   * @throws DomainException if the reading is refused
   */
  public Verdict judge(Caller caller, ReadingInput input, RuleSetView rules, boolean ageExempt) {
    return judgeFor(caller.aqueductId(), input, rules, ageExempt);
  }

  /**
   * Judges a reading of an aqueduct without a person behind it, such as one sent by a sensor.
   *
   * @param aqueductId the aqueduct of the tank
   * @param input the reading
   * @param rules the active rules
   * @param ageExempt true for simulated history of a demo aqueduct
   * @return the verdict
   * @throws DomainException if the reading is refused
   */
  public Verdict judgeFor(
      UUID aqueductId, ReadingInput input, RuleSetView rules, boolean ageExempt) {
    TankView tank =
        network
            .tank(input.tankId())
            .filter(TankView::active)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Optional<ReadingView> existing = readings.findById(input.id());
    if (existing.isPresent()) {
      return new Verdict(input, tank, replay(existing.get(), input), List.of());
    }
    List<ReadingIssue> found =
        ReadingValidationChain.run(
            ReadingValidationChain.standard(history(input, tank)),
            candidate(input, tank, rules, ageExempt));
    if (!catalogs.isActiveCode(WATER_APPEARANCE, input.waterAppearanceCode(), aqueductId)) {
      throw new FieldValidationException("water_appearance_code", "FIELD_VALUE_NOT_ALLOWED");
    }
    return new Verdict(input, tank, null, found.stream().map(ReadingRecorder::view).toList());
  }

  /**
   * Stores a reading that was judged, or returns the stored one for a retry.
   *
   * @param caller who sends it
   * @param verdict the verdict of {@link #judge}
   * @param rules the active rules
   * @param origin the source and the batch or import it came in
   * @return the stored reading
   * @throws DomainException with {@code DUPLICATE_READING} if the id was taken meanwhile
   */
  public RecordedReading store(
      Caller caller, Verdict verdict, RuleSetView rules, ReadingOrigin origin) {
    return storeFor(caller.aqueductId(), caller.userId(), verdict, rules, origin);
  }

  /**
   * Stores a judged reading of an aqueduct, recorded by an account or by no one (a sensor).
   *
   * @param aqueductId the aqueduct of the tank
   * @param recordedBy the account, or null for a sensor
   * @param verdict the verdict of {@link #judgeFor}
   * @param rules the active rules
   * @param origin the source and the batch, import or device it came in
   * @return the stored reading
   * @throws DomainException with {@code DUPLICATE_READING} if the id was taken meanwhile
   */
  public RecordedReading storeFor(
      UUID aqueductId, UUID recordedBy, Verdict verdict, RuleSetView rules, ReadingOrigin origin) {
    if (verdict.replay()) {
      return new RecordedReading(
          verdict.replayOf(), readings.issues(verdict.replayOf().id()), true);
    }
    ReadingInput input = verdict.input();
    ReadingView reading =
        new ReadingView(
            input.id(),
            verdict.tank().id(),
            input.gaugeValue(),
            input.waterAppearanceCode(),
            input.damageNoticed(),
            input.note(),
            input.observedAt(),
            clock.instant(),
            origin.source(),
            verdict.issues().isEmpty() ? ACCEPTED : FLAGGED,
            rules.id());
    if (!readings.insert(aqueductId, reading, recordedBy, origin)) {
      throw new DomainException(ErrorCode.DUPLICATE_READING);
    }
    for (IssueView issue : verdict.issues()) {
      readings.insertIssue(aqueductId, reading.id(), issue);
    }
    return new RecordedReading(
        readings.findById(reading.id()).orElse(reading), verdict.issues(), false);
  }

  /**
   * Trims a note and checks its size and lines.
   *
   * @param raw the note as sent
   * @return the note, or null when it is empty
   * @throws FieldValidationException if it is too long or has too many lines
   */
  public static String normalizeNote(String raw) {
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

  private ReadingHistory history(ReadingInput input, TankView tank) {
    return new ReadingHistory() {
      @Override
      public Optional<UUID> duplicateOf(BigDecimal value, Instant observedAt, int windowMinutes) {
        return readings.findDuplicate(tank.id(), value, observedAt, windowMinutes, input.id());
      }

      @Override
      public Optional<LevelAt> previous(Instant observedAt) {
        return readings
            .previousEffective(tank.id(), observedAt)
            .map(level -> new LevelAt(level.value(), level.observedAt()));
      }
    };
  }

  private ReadingCandidate candidate(
      ReadingInput input, TankView tank, RuleSetView rules, boolean ageExempt) {
    return new ReadingCandidate(
        input.gaugeValue(),
        input.observedAt(),
        clock.instant(),
        tank.gaugeMin(),
        tank.gaugeMax(),
        new ReadingLimits(
            rules.maxBackdateDays(), rules.duplicateWindowMinutes(), rules.maxLevelChangePerHour()),
        ageExempt);
  }

  private static IssueView view(ReadingIssue issue) {
    return new IssueView(issue.code(), issue.severity(), issue.details());
  }

  /**
   * The same client id again: the same content is a retry that returns the stored reading, other
   * content is a conflict.
   */
  private static ReadingView replay(ReadingView existing, ReadingInput input) {
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
            input.tankId(),
            input.gaugeValue(),
            input.waterAppearanceCode(),
            input.damageNoticed(),
            input.note(),
            input.observedAt());
    if (!stored.sameAs(incoming)) {
      throw new DomainException(ErrorCode.DUPLICATE_READING);
    }
    return existing;
  }
}
