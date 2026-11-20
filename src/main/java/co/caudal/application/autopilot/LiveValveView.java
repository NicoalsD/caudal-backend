package co.caudal.application.autopilot;

import java.time.Instant;
import java.util.UUID;

/**
 * The live state of a valve and its last command.
 *
 * @param valveId the valve
 * @param sectorId its sector
 * @param state OPEN, CLOSED or UNKNOWN
 * @param lastCommand the last command, or null
 */
public record LiveValveView(UUID valveId, UUID sectorId, String state, LastCommand lastCommand) {

  /**
   * The last command of a valve.
   *
   * @param id the command
   * @param action OPEN or CLOSE
   * @param status its status
   * @param origin SCHEDULE, MANUAL or AUTOPILOT
   * @param createdAt when it was created
   * @param updatedAt when it last changed
   */
  public record LastCommand(
      UUID id, String action, String status, String origin, Instant createdAt, Instant updatedAt) {}
}
