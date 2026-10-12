package co.caudal.application.incident;

import java.time.Instant;
import java.util.UUID;

/**
 * What the database tells about a report found by its tracking code.
 *
 * @param aqueductId the aqueduct, to find the label of the category
 * @param status the status
 * @param categoryCode the category
 * @param reportedAt when it was reported
 * @param updatedAt last change of status
 */
public record TrackedReport(
    UUID aqueductId, String status, String categoryCode, Instant reportedAt, Instant updatedAt) {}
