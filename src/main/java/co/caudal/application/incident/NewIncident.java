package co.caudal.application.incident;

import java.util.UUID;

/**
 * An incident to store.
 *
 * @param source PUBLIC_FORM, OPERATOR, ANOMALY or IMPORT
 * @param categoryCode a code of {@code DAMAGE_CATEGORY}
 * @param sectorId the sector, or null
 * @param description what happened
 * @param locationHint where, or null
 * @param trackingCodeHash hash of the tracking code
 * @param reporterIpHmac HMAC of the address, or null
 */
public record NewIncident(
    String source,
    String categoryCode,
    UUID sectorId,
    String description,
    String locationHint,
    String trackingCodeHash,
    String reporterIpHmac) {}
