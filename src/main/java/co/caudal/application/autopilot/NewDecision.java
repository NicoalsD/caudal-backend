package co.caudal.application.autopilot;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * A decision to store.
 *
 * @param aqueductId the aqueduct
 * @param source LLM or RULES
 * @param model the model or the version of the rules
 * @param action OPEN, CLOSE, WAIT or INSPECT
 * @param valveId the valve, or null
 * @param reason why
 * @param confidence from 0 to 1
 * @param status PENDING or SKIPPED
 * @param statusDetail the guard that skipped it, or null
 * @param inputs the snapshot that led to it, already reduced
 */
public record NewDecision(
    UUID aqueductId,
    String source,
    String model,
    String action,
    UUID valveId,
    String reason,
    BigDecimal confidence,
    String status,
    String statusDetail,
    Map<String, Object> inputs) {}
