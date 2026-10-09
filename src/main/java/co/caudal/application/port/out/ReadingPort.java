package co.caudal.application.port.out;

import co.caudal.application.reading.IssueView;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.reading.ListedReading;
import co.caudal.application.reading.ReadingView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes readings and their issues. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the tables have row level security.
 */
public interface ReadingPort {

  /**
   * Finds a reading.
   *
   * @param id the reading
   * @return the reading, or empty if it is not in the aqueduct of the session
   */
  Optional<ReadingView> findById(UUID id);

  /**
   * Lists the issues of a reading.
   *
   * @param readingId the reading
   * @return the issues
   */
  List<IssueView> issues(UUID readingId);

  /**
   * Finds another reading of the tank with the same effective value close in time.
   *
   * @param tankId the tank
   * @param value the value
   * @param observedAt the moment
   * @param windowMinutes half width of the window, in minutes
   * @param excludeId a reading to ignore
   * @return the id of one matching reading, or empty
   */
  Optional<UUID> findDuplicate(
      UUID tankId, BigDecimal value, Instant observedAt, int windowMinutes, UUID excludeId);

  /**
   * Finds the last effective reading before a moment.
   *
   * @param tankId the tank
   * @param before the moment
   * @return the reading, or empty
   */
  Optional<LevelPoint> previousEffective(UUID tankId, Instant before);

  /**
   * Stores a reading unless its id is taken.
   *
   * @param aqueductId the aqueduct
   * @param reading the reading
   * @param recordedBy the account
   * @return true if stored, false if the id already existed
   */
  boolean insert(UUID aqueductId, ReadingView reading, UUID recordedBy);

  /**
   * Stores an issue of a reading.
   *
   * @param aqueductId the aqueduct
   * @param readingId the reading
   * @param issue the issue
   */
  void insertIssue(UUID aqueductId, UUID readingId, IssueView issue);

  /**
   * Lists readings of a tank, newest first.
   *
   * @param tankId the tank
   * @param before only readings observed strictly before this moment, or null
   * @param limit maximum number of readings
   * @return the readings
   */
  List<ListedReading> list(UUID tankId, Instant before, int limit);

  /**
   * Lists the effective levels of a tank since a moment, oldest first.
   *
   * @param tankId the tank
   * @param since the start
   * @return the levels
   */
  List<LevelPoint> effectiveSince(UUID tankId, Instant since);

  /**
   * Finds the latest effective reading of a tank.
   *
   * @param tankId the tank
   * @return the reading, or empty
   */
  Optional<LevelPoint> latestEffective(UUID tankId);
}
