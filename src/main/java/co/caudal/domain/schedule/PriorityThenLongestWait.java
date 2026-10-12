package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The default allocation (docs/Reglas-de-negocio.md, section 5.1): sectors in the order of {@link
 * SectorTurnIterator}, each one a shift of {@code min(max_shift_hours, budget left)}, back to back
 * from the start of the window. A shift shorter than {@code min_shift_hours} is never made: that
 * sector and the ones after it stay without a shift ({@code NO_BUDGET}) and the leftover is kept.
 *
 * @pattern P19 Strategy
 */
public final class PriorityThenLongestWait implements TurnAllocationStrategy {

  /** Code of the strategy. */
  public static final String CODE = "PRIORITY_THEN_LONGEST_WAIT";

  private static final String NO_BUDGET = "NO_BUDGET";

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public void allocate(AllocationInput input, ScheduleProposalBuilder builder) {
    BigDecimal budget = input.budgetHours();
    Instant cursor = input.start();
    SectorTurnIterator sectors = new SectorTurnIterator(input.candidates(), input.priorityOnly());
    while (sectors.hasNext()) {
      SectorCandidate sector = sectors.next();
      if (budget.compareTo(input.minShiftHours()) < 0) {
        builder.unserved(sector.sectorId(), NO_BUDGET);
        continue;
      }
      BigDecimal hours = input.maxShiftHours().min(budget);
      Instant end = TurnAllocationStrategy.plusHours(cursor, hours);
      builder.turn(sector.sectorId(), cursor, end, input.reasons().apply(sector));
      cursor = end;
      budget = budget.subtract(hours);
    }
    builder.leftover(budget);
  }
}
