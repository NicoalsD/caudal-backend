package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to a damage report.
 *
 * @param trackingCode the code to follow it, shown only once
 * @param status the status
 * @param message the text to show
 */
@Schema(description = "Respuesta a un reporte de daño. El código se muestra una sola vez.")
public record DamageReportResponse(
    @Schema(example = "7K3M9QXR") String trackingCode,
    @Schema(example = "REPORTED") String status,
    String message) {}
