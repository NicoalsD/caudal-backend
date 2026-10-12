package co.caudal.application.incident;

import java.time.Instant;

/**
 * What the person who reported damage sees: never the description, the place or anyone's data.
 *
 * @param status the status code
 * @param statusLabelEs the status in Spanish
 * @param categoryLabelEs the category in Spanish
 * @param reportedAt when it was reported
 * @param updatedAt last change
 */
public record PublicReportStatus(
    String status,
    String statusLabelEs,
    String categoryLabelEs,
    Instant reportedAt,
    Instant updatedAt) {}
