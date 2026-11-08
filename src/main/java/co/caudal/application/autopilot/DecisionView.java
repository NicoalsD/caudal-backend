package co.caudal.application.autopilot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A decision of the autopilot as the application reads it.
 *
 * @param id the decision
 * @param createdAt when the cycle created it
 * @param source LLM or RULES
 * @param model the model or the version of the rules
 * @param action OPEN, CLOSE, WAIT or INSPECT
 * @param valveId the valve, or null
 * @param reason why
 * @param confidence from 0 to 1
 * @param status PENDING, ACCEPTED, REJECTED, EXECUTED, EXPIRED or SKIPPED
 * @param statusDetail why it was skipped or rejected, or null
 * @param decidedBy the person who resolved it, or null
 * @param decidedAt when it was resolved, or null
 * @param valveCommandId the command it created, or null
 */
public record DecisionView(
    UUID id,
    Instant createdAt,
    String source,
    String model,
    String action,
    UUID valveId,
    String reason,
    BigDecimal confidence,
    String status,
    String statusDetail,
    UUID decidedBy,
    Instant decidedAt,
    UUID valveCommandId) {}
