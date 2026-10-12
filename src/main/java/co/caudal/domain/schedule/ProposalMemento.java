package co.caudal.domain.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a proposal before the Board changes it (docs/Reglas-de-negocio.md, section
 * 12). It is taken only once, the first time the proposal is modified, so the original proposal of
 * the system can always be shown next to what the Board decided. It has no method that changes the
 * proposal; {@link #restore()} gives back the original shifts.
 *
 * @param proposalId the proposal
 * @param lockVersion its version when the snapshot was taken
 * @param strategyCode how the hours were handed out
 * @param items the shifts
 * @param takenAt when
 * @pattern P16 Memento
 */
public record ProposalMemento(
    UUID proposalId,
    int lockVersion,
    String strategyCode,
    List<ScheduleItemSnapshot> items,
    Instant takenAt) {

  /** Copies the shifts. */
  public ProposalMemento {
    items = List.copyOf(items);
  }

  /**
   * The original shifts, as the system proposed them.
   *
   * @return the shifts in their order
   */
  public List<ProposedTurn> restore() {
    return items.stream()
        .map(
            i ->
                new ProposedTurn(
                    i.sectorId(), i.sequence(), i.start(), i.end(), i.origin(), i.reasons()))
        .toList();
  }
}
