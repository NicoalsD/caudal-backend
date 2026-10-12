package co.caudal.api.dto.response;

/**
 * The error of a rejected point of a batch.
 *
 * @param code the error code, such as {@code GAUGE_OUT_OF_RANGE}
 */
public record TelemetryErrorResponse(String code) {}
