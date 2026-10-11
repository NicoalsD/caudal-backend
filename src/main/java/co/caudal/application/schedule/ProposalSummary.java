package co.caudal.application.schedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A proposal without its shifts.
 *
 * @param id the proposal
 * @param serviceDate the day
 * @param status its status
 * @param ruleSetId the rules used
 * @param tankLevelSnapshot the level it relied on
 * @param tankBand its band
 * @param availableHours the hours of the day
 * @param forecastRunId the forecast used, or null
 * @param strategyCode the strategy
 * @param generatedAt when it was made
 * @param lockVersion version for concurrent decisions
 */
public record ProposalSummary(
    UUID id,
    LocalDate serviceDate,
    String status,
    UUID ruleSetId,
    BigDecimal tankLevelSnapshot,
    String tankBand,
    BigDecimal availableHours,
    UUID forecastRunId,
    String strategyCode,
    Instant generatedAt,
    int lockVersion) {}
