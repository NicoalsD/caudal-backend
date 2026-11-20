package co.caudal.application.autopilot;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One recommendation as the assistant or the rules produced it, before the backend checks it.
 *
 * @param action OPEN, CLOSE, WAIT or INSPECT
 * @param valveId the valve, or null
 * @param reason why, in Spanish
 * @param confidence from 0 to 1
 */
public record InsightRecommendation(
    String action, UUID valveId, String reason, BigDecimal confidence) {}
