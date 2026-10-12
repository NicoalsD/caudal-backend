package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The closure of a day.
 *
 * @param id the closure
 * @param serviceDate the day
 * @param notes the notes
 * @param closedBy who closed it
 * @param closedAt when
 * @param executions the executions of the day, only when reading it
 */
@Schema(description = "Cierre del día con sus ejecuciones.")
public record ClosureResponse(
    UUID id,
    LocalDate serviceDate,
    @Schema(nullable = true) String notes,
    UUID closedBy,
    Instant closedAt,
    @Schema(nullable = true) List<ExecutionResponse> executions) {}
