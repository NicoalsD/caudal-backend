package co.caudal.application.schedule;

import java.time.Instant;
import java.util.UUID;

/**
 * A shift as the Board leaves it.
 *
 * @param id the existing shift, or null for a new one
 * @param sectorId the sector
 * @param startAt start
 * @param endAt end
 * @param sequence the order the Board wants
 */
public record ModifiedItem(UUID id, UUID sectorId, Instant startAt, Instant endAt, int sequence) {}
