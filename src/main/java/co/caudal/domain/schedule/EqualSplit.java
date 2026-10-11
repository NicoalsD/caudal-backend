package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * The alternative allocation (docs/Reglas-de-negocio.md, section 5.3): the budget is split in equal
 * shares of {@code min(max_shift_hours, budget / N)} among the N sectors in the walk, in the same
 * order as the default strategy. If the share is shorter than {@code min_shift_hours}, nobody gets
 * a shift ({@code CUOTA_INSUFICIENTE}).
 *
 * @pattern P19 Strategy
 */
public final class EqualSplit implements TurnAllocationStrategy {

  /** Code of the strategy. */
  public static final String CODE = "EQUAL_SPLIT";

  private static final String SHARE_TOO_SMALL = "CUOTA_INSUFICIENTE";
  private static final int SCALE = 2;

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public void allocate(AllocationInput input, ScheduleProposalBuilder builder) {
    SectorTurnIterator sectors = new SectorTurnIterator(input.candidates(), input.priorityOnly());
    if (sectors.size() == 0) {
      builder.leftover(input.budgetHours());
      return;
    }
    BigDecimal share =
        input
            .maxShiftHours()
            .min(
                input
                    .budgetHours()
                    .divide(BigDecimal.valueOf(sectors.size()), SCALE, RoundingMode.DOWN));
    if (share.compareTo(input.minShiftHours()) < 0) {
      sectors.forEachRemaining(s -> builder.unserved(s.sectorId(), SHARE_TOO_SMALL));
      builder.leftover(input.budgetHours());
      return;
    }
    Instant cursor = input.start();
    BigDecimal used = BigDecimal.ZERO;
    while (sectors.hasNext()) {
      SectorCandidate sector = sectors.next();
      Instant end = TurnAllocationStrategy.plusHours(cursor, share);
      builder.turn(sector.sectorId(), cursor, end, input.reasons().apply(sector));
      cursor = end;
      used = used.add(share);
    }
    builder.leftover(input.budgetHours().subtract(used));
  }
}
