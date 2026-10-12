package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * What happened in a shift.
 *
 * @param id the execution
 * @param scheduleItemId the shift
 * @param status COMPLETED, PARTIAL or NOT_EXECUTED
 * @param actualStart real start
 * @param actualEnd real end
 * @param note the note
 * @param supersedesId the execution it replaces
 * @param recordedBy who recorded it
 * @param recordedAt when
 */
@Schema(description = "Ejecución real de un turno (dato CONFIRMED).")
public record ExecutionResponse(
    UUID id,
    UUID scheduleItemId,
    @Schema(example = "PARTIAL") String status,
    @Schema(nullable = true) Instant actualStart,
    @Schema(nullable = true) Instant actualEnd,
    @Schema(nullable = true) String note,
    @Schema(nullable = true) UUID supersedesId,
    UUID recordedBy,
    Instant recordedAt) {

  /**
   * Maps an execution.
   *
   * @param e the execution
   * @return the response
   */
  public static ExecutionResponse of(co.caudal.application.execution.ExecutionView e) {
    return new ExecutionResponse(
        e.id(),
        e.scheduleItemId(),
        e.status(),
        e.actualStart(),
        e.actualEnd(),
        e.note(),
        e.supersedesId(),
        e.recordedBy(),
        e.recordedAt());
  }
}
