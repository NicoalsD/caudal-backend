package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A proposal of shifts for one day of service, before it is stored.
 *
 * @param serviceDate the day
 * @param ruleSetId the rules used
 * @param tankLevel the last observed level it relies on
 * @param tankBand the band of that level
 * @param availableHours hours of service the tank allows today
 * @param forecastRunId the forecast used for the reserve guard, or null
 * @param strategyCode the allocation strategy
 * @param turns the shifts, in order
 * @param unserved the sectors without a shift and why
 * @param leftoverHours hours left without use (less than a minimum shift)
 */
public record ScheduleProposal(
    LocalDate serviceDate,
    UUID ruleSetId,
    BigDecimal tankLevel,
    String tankBand,
    BigDecimal availableHours,
    UUID forecastRunId,
    String strategyCode,
    List<ProposedTurn> turns,
    List<UnservedSector> unserved,
    BigDecimal leftoverHours) {

  /** Copies the lists. */
  public ScheduleProposal {
    turns = List.copyOf(turns);
    unserved = List.copyOf(unserved);
  }
}
