package co.caudal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * The day to propose shifts for.
 *
 * @param serviceDate the day of service
 */
@Schema(description = "Día para el que se proponen turnos.")
public record GenerateProposalRequest(
    @Schema(description = "Día de servicio (YYYY-MM-DD).", example = "2026-10-09") @NotNull
        LocalDate serviceDate) {}
