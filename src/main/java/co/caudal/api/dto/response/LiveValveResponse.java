package co.caudal.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The live state of a valve.
 *
 * @param valveId the valve
 * @param sectorId its sector
 * @param state OPEN, CLOSED or UNKNOWN
 * @param lastCommand the last command, or null
 */
@Schema(description = "Estado en vivo de una llave y su último comando.")
public record LiveValveResponse(
    UUID valveId,
    UUID sectorId,
    @Schema(description = "OPEN, CLOSED o UNKNOWN (sin comando confirmado).") String state,
    @Schema(nullable = true) LastCommand lastCommand) {

  /**
   * The last command of a valve.
   *
   * @param id the command
   * @param action OPEN or CLOSE
   * @param status PENDING, DELIVERED, ACKED, FAILED, EXPIRED or CANCELLED
   * @param origin SCHEDULE, MANUAL or AUTOPILOT
   * @param createdAt when it was created
   * @param updatedAt when it last changed
   */
  @Schema(description = "Último comando de la llave.")
  public record LastCommand(
      UUID id,
      String action,
      String status,
      @Schema(description = "SCHEDULE (turno), MANUAL o AUTOPILOT.") String origin,
      Instant createdAt,
      Instant updatedAt) {}
}
