package co.caudal.domain.reading.validation;

import co.caudal.domain.reading.ReadingRules;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flags a reading when the level moved faster than {@code max_level_change_per_hour} since the last
 * effective reading before it (rule 4, {@code SUDDEN_JUMP}). The reading is kept, marked for a
 * person to look at: a jump may be a misreading or a real leak.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class SuddenJumpHandler extends ReadingValidationHandler {

  private final ReadingHistory history;

  /**
   * Creates the link.
   *
   * @param history the earlier readings of the tank
   */
  public SuddenJumpHandler(ReadingHistory history) {
    this.history = Objects.requireNonNull(history, "history");
  }

  @Override
  protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
    BigDecimal max = candidate.limits().maxLevelChangePerHour();
    history
        .previous(candidate.observedAt())
        .map(
            previous ->
                ReadingRules.changePerHour(
                    previous.value(),
                    previous.observedAt(),
                    candidate.value(),
                    candidate.observedAt()))
        .filter(rate -> rate != null && rate.compareTo(max) > 0)
        .ifPresent(
            rate -> {
              Map<String, Object> details = new LinkedHashMap<>();
              details.put("rate_per_hour", rate);
              details.put("max_per_hour", max);
              issues.add(new ReadingIssue(ReadingIssue.SUDDEN_JUMP, ReadingIssue.WARNING, details));
            });
  }
}
