package co.caudal.application.simulation;

import java.time.Instant;
import java.util.UUID;

/**
 * A simulated execution of a shift.
 *
 * @param scheduleItemId the shift of the schedule
 * @param status {@code COMPLETED}, {@code PARTIAL} or {@code NOT_EXECUTED}
 * @param actualStart when the valve really opened, or null
 * @param actualEnd when it really closed, or null
 * @param note a note, or null
 */
public record ShiftExecutionRow(
    UUID scheduleItemId, String status, Instant actualStart, Instant actualEnd, String note) {}
