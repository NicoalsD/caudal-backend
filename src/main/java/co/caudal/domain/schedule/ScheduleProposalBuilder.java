package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Assembles a {@link ScheduleProposal} step by step while the allocation runs: first the facts of
 * the day, then each shift with its reasons, then the sectors left out. Shifts get their sequence
 * in the order they are added, and {@link #build()} refuses a proposal without its facts.
 *
 * @pattern P04 Builder
 */
public final class ScheduleProposalBuilder {

  private static final String PROPOSED = "PROPOSED";

  private LocalDate serviceDate;
  private UUID ruleSetId;
  private BigDecimal tankLevel;
  private String tankBand;
  private BigDecimal availableHours;
  private UUID forecastRunId;
  private String strategyCode;
  private BigDecimal leftoverHours = BigDecimal.ZERO;
  private final List<ProposedTurn> turns = new ArrayList<>();
  private final List<UnservedSector> unserved = new ArrayList<>();

  /**
   * Sets the day and the rules.
   *
   * @param day the day of service
   * @param rules the rules in force
   * @return this builder
   */
  public ScheduleProposalBuilder forDay(LocalDate day, UUID rules) {
    this.serviceDate = day;
    this.ruleSetId = rules;
    return this;
  }

  /**
   * Sets the level the proposal relies on.
   *
   * @param level the last observed level
   * @param band its band
   * @return this builder
   */
  public ScheduleProposalBuilder level(BigDecimal level, String band) {
    this.tankLevel = level;
    this.tankBand = band;
    return this;
  }

  /**
   * Sets the hours of service of the day.
   *
   * @param hours the available hours
   * @return this builder
   */
  public ScheduleProposalBuilder availableHours(BigDecimal hours) {
    this.availableHours = hours;
    return this;
  }

  /**
   * Sets the forecast used for the reserve guard.
   *
   * @param runId the run, or null
   * @return this builder
   */
  public ScheduleProposalBuilder forecast(UUID runId) {
    this.forecastRunId = runId;
    return this;
  }

  /**
   * Sets the allocation strategy.
   *
   * @param code the strategy
   * @return this builder
   */
  public ScheduleProposalBuilder strategy(String code) {
    this.strategyCode = code;
    return this;
  }

  /**
   * Adds the next shift of the day.
   *
   * @param sectorId the sector
   * @param start when it starts
   * @param end when it ends
   * @param reasons why
   * @return this builder
   */
  public ScheduleProposalBuilder turn(
      UUID sectorId, Instant start, Instant end, List<TurnReason> reasons) {
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("A shift must end after it starts");
    }
    turns.add(new ProposedTurn(sectorId, turns.size() + 1, start, end, PROPOSED, reasons));
    return this;
  }

  /**
   * Records a sector left without a shift.
   *
   * @param sectorId the sector
   * @param reason why
   * @return this builder
   */
  public ScheduleProposalBuilder unserved(UUID sectorId, String reason) {
    unserved.add(new UnservedSector(sectorId, reason));
    return this;
  }

  /**
   * Records the hours left without use.
   *
   * @param hours the leftover
   * @return this builder
   */
  public ScheduleProposalBuilder leftover(BigDecimal hours) {
    this.leftoverHours = hours;
    return this;
  }

  /**
   * Builds the proposal.
   *
   * @return the proposal
   * @throws NullPointerException if a fact of the day is missing
   */
  public ScheduleProposal build() {
    return new ScheduleProposal(
        Objects.requireNonNull(serviceDate, "serviceDate"),
        Objects.requireNonNull(ruleSetId, "ruleSetId"),
        Objects.requireNonNull(tankLevel, "tankLevel"),
        Objects.requireNonNull(tankBand, "tankBand"),
        Objects.requireNonNull(availableHours, "availableHours"),
        forecastRunId,
        Objects.requireNonNull(strategyCode, "strategyCode"),
        turns,
        unserved,
        leftoverHours);
  }
}
