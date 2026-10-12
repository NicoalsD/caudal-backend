package co.caudal.domain.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shift as it was when the snapshot was taken.
 *
 * @param itemId the shift
 * @param sectorId the sector
 * @param sequence its order
 * @param start start
 * @param end end
 * @param origin PROPOSED, MODIFIED or ADDED
 * @param reasons why it was given
 */
public record ScheduleItemSnapshot(
    UUID itemId,
    UUID sectorId,
    int sequence,
    Instant start,
    Instant end,
    String origin,
    List<TurnReason> reasons) {

  /** Copies the reasons. */
  public ScheduleItemSnapshot {
    reasons = List.copyOf(reasons);
  }
}
