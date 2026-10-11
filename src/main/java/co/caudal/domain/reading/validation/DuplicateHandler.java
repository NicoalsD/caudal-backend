package co.caudal.domain.reading.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flags a reading whose value repeats another reading of the tank inside the window of the rules
 * (rule 3, {@code DUPLICATE}). A repeated value is not an error: the reading is stored {@code
 * FLAGGED} with the id of the other one.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class DuplicateHandler extends ReadingValidationHandler {

  private final ReadingHistory history;

  /**
   * Creates the link.
   *
   * @param history the earlier readings of the tank
   */
  public DuplicateHandler(ReadingHistory history) {
    this.history = Objects.requireNonNull(history, "history");
  }

  @Override
  protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
    int window = candidate.limits().duplicateWindowMinutes();
    history
        .duplicateOf(candidate.value(), candidate.observedAt(), window)
        .ifPresent(
            matched -> {
              Map<String, Object> details = new LinkedHashMap<>();
              details.put("window_minutes", window);
              details.put("matched_reading_id", matched.toString());
              issues.add(new ReadingIssue(ReadingIssue.DUPLICATE, ReadingIssue.INFO, details));
            });
  }
}
