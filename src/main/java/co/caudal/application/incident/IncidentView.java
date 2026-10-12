package co.caudal.application.incident;

import java.time.Instant;
import java.util.UUID;

/**
 * An incident as the Board and the field see it (docs/API.md, section 12.1). It never carries the
 * hash of the tracking code nor the HMAC of the address of the reporter.
 *
 * @param id the incident
 * @param source PUBLIC_FORM, OPERATOR, ANOMALY or IMPORT
 * @param categoryCode a code of {@code DAMAGE_CATEGORY}
 * @param sectorId the sector, or null
 * @param description what happened
 * @param locationHint where, or null
 * @param status the status of section 15 of the rules
 * @param reportedAt when it was reported
 */
public record IncidentView(
    UUID id,
    String source,
    String categoryCode,
    UUID sectorId,
    String description,
    String locationHint,
    String status,
    Instant reportedAt) {}
