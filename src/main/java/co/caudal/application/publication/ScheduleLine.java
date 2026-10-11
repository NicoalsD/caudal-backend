package co.caudal.application.publication;

import java.time.Instant;

/**
 * One shift as the public sees it: only the sector and the hours.
 *
 * @param sectorName the sector
 * @param startAt start
 * @param endAt end
 */
public record ScheduleLine(String sectorName, Instant startAt, Instant endAt) {}
