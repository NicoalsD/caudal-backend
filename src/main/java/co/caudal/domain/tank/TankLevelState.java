package co.caudal.domain.tank;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * What can be said about the level of a tank right now (docs/Reglas-de-negocio.md, sections 10.1
 * and 10.3): nothing (no reading yet), a recent level, or a level that is too old to trust. Each
 * state says whether the data is stale and whether a {@code STALE_DATA} anomaly must be opened.
 *
 * @pattern P18 State
 */
public sealed interface TankLevelState
    permits TankLevelState.NoData, TankLevelState.Fresh, TankLevelState.Stale {

  /**
   * Picks the state of a tank.
   *
   * @param value the last effective level, or null if there is none
   * @param observedAt when it was read, or null
   * @param now the current moment
   * @param staleAfterHours {@code stale_reading_hours} of the rules
   * @return the state
   */
  static TankLevelState of(BigDecimal value, Instant observedAt, Instant now, int staleAfterHours) {
    if (value == null || observedAt == null) {
      return new NoData();
    }
    Duration age = Duration.between(observedAt, now);
    return age.compareTo(Duration.ofHours(staleAfterHours)) > 0
        ? new Stale(value, observedAt, age)
        : new Fresh(value, observedAt, age);
  }

  /**
   * Whether the level is too old.
   *
   * @return true when stale
   */
  boolean stale();

  /**
   * Whether the state calls for a {@code STALE_DATA} anomaly.
   *
   * @return true when stale
   */
  default boolean opensStaleAnomaly() {
    return stale();
  }

  /** The tank has no effective reading yet. */
  record NoData() implements TankLevelState {
    @Override
    public boolean stale() {
      return false;
    }
  }

  /**
   * A level read recently enough.
   *
   * @param value the level
   * @param observedAt when it was read
   * @param age how long ago
   */
  record Fresh(BigDecimal value, Instant observedAt, Duration age) implements TankLevelState {
    @Override
    public boolean stale() {
      return false;
    }
  }

  /**
   * A level older than {@code stale_reading_hours}.
   *
   * @param value the level
   * @param observedAt when it was read
   * @param age how long ago
   */
  record Stale(BigDecimal value, Instant observedAt, Duration age) implements TankLevelState {
    @Override
    public boolean stale() {
      return true;
    }
  }
}
