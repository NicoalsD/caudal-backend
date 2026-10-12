package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The status of a damage report as its reporter sees it.
 *
 * @param status the status code
 * @param statusLabelEs the status in Spanish
 * @param categoryLabelEs the category in Spanish
 * @param reportedAt when it was reported
 * @param updatedAt last change
 */
@Schema(description = "Estado de un reporte de daño. Sin datos del reportante ni IP.")
public record PublicReportStatusResponse(
    @Schema(example = "VERIFYING") String status,
    @Schema(example = "En revisión") String statusLabelEs,
    @Schema(example = "Fuga") String categoryLabelEs,
    Instant reportedAt,
    Instant updatedAt) {}
