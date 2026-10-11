package co.caudal.domain.reading.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The earlier readings of the same tank that some checks compare against. */
public interface ReadingHistory {

  /**
   * Finds another reading of the tank with the same value inside the window.
   *
   * @param value the value
   * @param observedAt the moment of the new reading
   * @param windowMinutes minutes before and after
   * @return the id of the reading found, or empty
   */
  Optional<UUID> duplicateOf(BigDecimal value, Instant observedAt, int windowMinutes);

  /**
   * The last effective level of the tank before a moment.
   *
   * @param observedAt the moment of the new reading
   * @return the earlier level, or empty if there is none
   */
  Optional<LevelAt> previous(Instant observedAt);

  /**
   * A level at a moment.
   *
   * @param value the level
   * @param observedAt when it was read
   */
  record LevelAt(BigDecimal value, Instant observedAt) {}

  /**
   * A history without earlier readings.
   *
   * @return an empty history
   */
  static ReadingHistory empty() {
    return new ReadingHistory() {
      @Override
      public Optional<UUID> duplicateOf(BigDecimal value, Instant observedAt, int windowMinutes) {
        return Optional.empty();
      }

      @Override
      public Optional<LevelAt> previous(Instant observedAt) {
        return Optional.empty();
      }
    };
  }
}
