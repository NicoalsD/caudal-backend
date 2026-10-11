package co.caudal.application.execution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The closure of a day of service.
 *
 * @param id the closure
 * @param serviceDate the day
 * @param notes the notes, or null
 * @param closedBy who closed it
 * @param closedAt when
 */
public record ClosureView(
    UUID id, LocalDate serviceDate, String notes, UUID closedBy, Instant closedAt) {}
