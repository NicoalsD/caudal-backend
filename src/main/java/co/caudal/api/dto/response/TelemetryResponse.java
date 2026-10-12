package co.caudal.api.dto.response;

import java.util.List;

/**
 * Answer to a batch of telemetry (docs/API.md, section 16.1).
 *
 * @param accepted points stored now
 * @param duplicates points already stored with the same content
 * @param rejected points not stored
 * @param results one result per point, in the order received
 */
public record TelemetryResponse(
    long accepted, long duplicates, long rejected, List<TelemetryPointResponse> results) {}
