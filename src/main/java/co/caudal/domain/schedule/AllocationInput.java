package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * What a strategy needs to hand out the hours of the day.
 *
 * @param candidates every sector of the aqueduct
 * @param priorityOnly true when only priority sectors get water today
 * @param budgetHours hours of service of the day
 * @param start when the first shift starts (the start of the operating window)
 * @param minShiftHours shortest shift
 * @param maxShiftHours longest shift
 * @param reasons the explanations of the shift of a sector
 */
public record AllocationInput(
    List<SectorCandidate> candidates,
    boolean priorityOnly,
    BigDecimal budgetHours,
    Instant start,
    BigDecimal minShiftHours,
    BigDecimal maxShiftHours,
    Function<SectorCandidate, List<TurnReason>> reasons) {

  /** Copies the candidates. */
  public AllocationInput {
    candidates = List.copyOf(candidates);
  }
}
