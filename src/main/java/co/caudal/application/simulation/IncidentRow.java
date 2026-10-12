package co.caudal.application.simulation;

import java.time.Instant;
import java.util.UUID;

/**
 * A simulated report of damage.
 *
 * @param categoryCode code of the catalog {@code DAMAGE_CATEGORY}
 * @param sectorId the sector, or null
 * @param description what happened, 10 to 500 characters
 * @param locationHint where, or null
 * @param reportedAt when it was reported
 */
public record IncidentRow(
    String categoryCode,
    UUID sectorId,
    String description,
    String locationHint,
    Instant reportedAt) {}
