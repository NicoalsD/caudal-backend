package co.caudal.application.execution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A shift with what decides whether it can get an execution.
 *
 * @param itemId the shift
 * @param proposalStatus status of its proposal
 * @param serviceDate the day of service
 * @param startAt planned start
 * @param endAt planned end
 */
public record ShiftContext(
    UUID itemId, String proposalStatus, LocalDate serviceDate, Instant startAt, Instant endAt) {}
