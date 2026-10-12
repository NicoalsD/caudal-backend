package co.caudal.application.incident;

import java.time.Instant;
import java.util.UUID;

/**
 * The filters of the list of incidents. Any of them may be null.
 *
 * @param status only this status
 * @param source only this origin
 * @param sectorId only this sector
 * @param from only incidents reported at or after this moment
 * @param to only incidents reported at or before this moment
 */
public record IncidentFilter(
    String status, String source, UUID sectorId, Instant from, Instant to) {}
