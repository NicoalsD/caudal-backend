package co.caudal.application.execution;

import java.time.Instant;
import java.util.UUID;

/**
 * An execution to store.
 *
 * @param scheduleItemId the shift
 * @param status COMPLETED, PARTIAL or NOT_EXECUTED
 * @param actualStart real start, or null
 * @param actualEnd real end, or null
 * @param note the note, or null
 * @param supersedesId the execution it replaces, or null
 * @param recordedBy who records it
 */
public record NewExecution(
    UUID scheduleItemId,
    String status,
    Instant actualStart,
    Instant actualEnd,
    String note,
    UUID supersedesId,
    UUID recordedBy) {}
