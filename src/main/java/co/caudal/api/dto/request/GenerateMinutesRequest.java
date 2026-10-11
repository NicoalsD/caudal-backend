package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The period of the minutes.
 *
 * @param periodFrom first day
 * @param periodTo last day, not before the first
 */
@Schema(description = "Periodo del acta.")
public record GenerateMinutesRequest(
    @Schema(description = "Primer día.", example = "2026-10-01") @NotNull LocalDate periodFrom,
    @Schema(description = "Último día, igual o posterior al primero.", example = "2026-10-07")
        @NotNull
        LocalDate periodTo) {}
