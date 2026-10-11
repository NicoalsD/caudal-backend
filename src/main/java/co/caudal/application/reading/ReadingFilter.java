package co.caudal.application.reading;

import java.time.Instant;
import java.util.UUID;

/**
 * What to list: the readings of one tank, optionally between two moments and with one status.
 *
 * @param tankId the tank
 * @param from only readings observed at or after this moment, or null
 * @param to only readings observed at or before this moment, or null
 * @param status only readings with this status, or null for all
 */
public record ReadingFilter(UUID tankId, Instant from, Instant to, ValidationStatus status) {}
