package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * A way to hand out the hours of service of a day among the sectors (docs/Reglas-de-negocio.md,
 * section 5). The Board picks it in {@code rule_sets.allocation_strategy}.
 *
 * @pattern P19 Strategy
 */
public interface TurnAllocationStrategy {

  /** Seconds in one hour, to turn hours of the rules into time. */
  BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(Duration.ofHours(1).toSeconds());

  /**
   * The code stored in {@code schedule_proposals.strategy_code}.
   *
   * @return the code
   */
  String code();

  /**
   * Adds the shifts, the sectors left out and the leftover hours to the builder.
   *
   * @param input the day
   * @param builder the proposal being built
   */
  void allocate(AllocationInput input, ScheduleProposalBuilder builder);

  /**
   * The strategy of a code.
   *
   * @param code {@code PRIORITY_THEN_LONGEST_WAIT} or {@code EQUAL_SPLIT}
   * @return the strategy
   * @throws IllegalArgumentException for an unknown code
   */
  static TurnAllocationStrategy forCode(String code) {
    return switch (code) {
      case PriorityThenLongestWait.CODE -> new PriorityThenLongestWait();
      case EqualSplit.CODE -> new EqualSplit();
      default -> throw new IllegalArgumentException("Unknown allocation strategy");
    };
  }

  /**
   * The moment some hours after another.
   *
   * @param from the start
   * @param hours the hours
   * @return the end
   */
  static Instant plusHours(Instant from, BigDecimal hours) {
    return from.plusSeconds(
        hours.multiply(SECONDS_PER_HOUR).setScale(0, RoundingMode.HALF_UP).longValueExact());
  }
}
