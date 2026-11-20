package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * The period of a narrative report.
 *
 * @param from start of the period, inclusive
 * @param to end of the period, exclusive
 */
@Schema(description = "Periodo del reporte narrativo.")
public record NarrativeReportRequest(
    @Schema(description = "Inicio del periodo, inclusive.") @NotNull Instant from,
    @Schema(description = "Fin del periodo, exclusivo; como máximo 92 días después de from.")
        @NotNull
        Instant to) {}
