package co.caudal.domain.schedule;

import java.time.Instant;
import java.util.UUID;

/**
 * A shift as the Board leaves it.
 *
 * @param sectorId the sector
 * @param start start
 * @param end end
 */
public record EditedShift(UUID sectorId, Instant start, Instant end) {}
