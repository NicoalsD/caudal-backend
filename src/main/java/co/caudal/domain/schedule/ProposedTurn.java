package co.caudal.domain.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A shift of a proposal.
 *
 * @param sectorId the sector
 * @param sequence its position in the day, from 1
 * @param start when the valve opens
 * @param end when it closes
 * @param origin {@code PROPOSED}, {@code MODIFIED} or {@code ADDED}
 * @param reasons why, in the order they are shown
 */
public record ProposedTurn(
    UUID sectorId,
    int sequence,
    Instant start,
    Instant end,
    String origin,
    List<TurnReason> reasons) {

  /** Copies the reasons. */
  public ProposedTurn {
    reasons = List.copyOf(reasons);
  }
}
