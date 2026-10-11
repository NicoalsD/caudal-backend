package co.caudal.application.schedule;

import java.util.List;

/**
 * A full proposal (docs/API.md, sections 10.1 and 10.3).
 *
 * @param summary the proposal
 * @param items its shifts
 * @param unserved sectors without a shift and why
 * @param decisions decisions of the Board, oldest first
 * @param snapshotTaken true when the Board changed shifts and the original was kept
 */
public record ProposalDetail(
    ProposalSummary summary,
    List<ItemView> items,
    List<UnservedView> unserved,
    List<DecisionView> decisions,
    boolean snapshotTaken) {

  /** Copies the lists. */
  public ProposalDetail {
    items = List.copyOf(items);
    unserved = List.copyOf(unserved);
    decisions = List.copyOf(decisions);
  }
}
