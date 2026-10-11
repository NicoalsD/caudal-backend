package co.caudal.application.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shift of a stored proposal.
 *
 * @param id the shift
 * @param sectorId the sector
 * @param sequence its position
 * @param startAt when it opens
 * @param endAt when it closes
 * @param origin {@code PROPOSED}, {@code MODIFIED} or {@code ADDED}
 * @param reasons why
 */
public record ItemView(
    UUID id,
    UUID sectorId,
    int sequence,
    Instant startAt,
    Instant endAt,
    String origin,
    List<ReasonView> reasons) {

  /** Copies the reasons. */
  public ItemView {
    reasons = List.copyOf(reasons);
  }
}
