package co.caudal.application.forecast;

import java.time.Instant;
import java.util.UUID;

/**
 * A forecast run to store in {@code ops.forecast_runs} with its points.
 *
 * @param aqueductId the aqueduct
 * @param tankId the tank
 * @param forecast the model, its version and its points
 * @param fallback true when the simple estimate replaced the service
 * @param fallbackReason why, {@code SHADOW_BASELINE} for the shadow, or null
 * @param horizonDays days forecast
 * @param contextFrom first moment of the history used
 * @param contextTo last moment of the history used
 * @param inputPoints readings used
 * @param ruleSetId rules in force
 * @param requestedBy who asked, or null for a scheduled run
 */
public record ForecastRunRecord(
    UUID aqueductId,
    UUID tankId,
    ModelForecast forecast,
    boolean fallback,
    String fallbackReason,
    int horizonDays,
    Instant contextFrom,
    Instant contextTo,
    int inputPoints,
    UUID ruleSetId,
    UUID requestedBy) {}
