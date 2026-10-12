package co.caudal.api.dto.response;

import java.util.UUID;

/**
 * What happened to one point of a batch.
 *
 * @param id the point
 * @param status ACCEPTED, DUPLICATE or REJECTED
 * @param readingId the reading created or already created, or null
 * @param validationStatus ACCEPTED or FLAGGED for a reading, or null
 * @param error the error of a rejected point, or null
 */
public record TelemetryPointResponse(
    UUID id,
    String status,
    UUID readingId,
    String validationStatus,
    TelemetryErrorResponse error) {}
