package co.caudal.api.dto.request;

import co.caudal.shared.FieldLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What happened in a shift.
 *
 * @param status COMPLETED, PARTIAL or NOT_EXECUTED
 * @param actualStart real start; required for PARTIAL
 * @param actualEnd real end, after the start
 * @param note the note, up to 500 characters and 10 lines
 * @param supersedesId the execution it replaces
 */
@Schema(description = "Ejecución real de un turno.")
public record RecordExecutionRequest(
    @Schema(description = "COMPLETED, PARTIAL o NOT_EXECUTED.", example = "PARTIAL")
        @NotNull
        @Pattern(regexp = FieldLimits.EXECUTION_STATUS_PATTERN)
        String status,
    @Schema(description = "Inicio real; obligatorio en PARTIAL.", nullable = true)
        OffsetDateTime actualStart,
    @Schema(description = "Fin real, después del inicio.", nullable = true)
        OffsetDateTime actualEnd,
    @Schema(
            description = "Nota, hasta 500 caracteres y 10 líneas.",
            example = "Se cortó el agua a las 09:30 por falla de la bomba.")
        @Size(max = FieldLimits.TEXT_GLOBAL_MAX)
        String note,
    @Schema(description = "Ejecución anterior que esta reemplaza.", nullable = true)
        UUID supersedesId) {}
