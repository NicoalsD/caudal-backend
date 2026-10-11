package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A proposal of shifts without its shifts.
 *
 * @param id the proposal
 * @param serviceDate the day of service
 * @param status its status
 * @param ruleSetId the rules used
 * @param tankLevelSnapshot the level used
 * @param tankBand its band
 * @param availableHours hours of service of the day
 * @param forecastRunId the forecast used, or null
 * @param strategyCode how the hours were handed out
 * @param generatedAt when it was made
 * @param lockVersion version for the next decision
 */
@Schema(description = "Propuesta de turnos sin sus turnos.")
@SuppressWarnings("checkstyle:ParameterNumber")
public record ProposalSummaryResponse(
    UUID id,
    LocalDate serviceDate,
    @Schema(example = "PENDING_REVIEW") String status,
    UUID ruleSetId,
    @Schema(example = "2.10") BigDecimal tankLevelSnapshot,
    @Schema(example = "LOW") String tankBand,
    @Schema(example = "8.00") BigDecimal availableHours,
    @Schema(nullable = true) UUID forecastRunId,
    @Schema(example = "PRIORITY_THEN_LONGEST_WAIT") String strategyCode,
    Instant generatedAt,
    int lockVersion) {}
