package co.caudal.application.device.command;

import java.time.Instant;
import java.util.UUID;

/**
 * A valve command as the application reads it (docs/Protocolo-de-dispositivos.md, section 5).
 *
 * @param id the command
 * @param aqueductId the aqueduct that owns it
 * @param deviceId the actuator that receives it
 * @param valveId the valve it moves
 * @param command OPEN or CLOSE
 * @param status the status, one of the codes of {@code ValveCommandState}
 * @param notBefore the command must not run before this moment
 * @param expiresAt the command must not run from this moment on
 */
public record ValveCommandView(
    UUID id,
    UUID aqueductId,
    UUID deviceId,
    UUID valveId,
    String command,
    String status,
    Instant notBefore,
    Instant expiresAt) {

  /**
   * The same command with another status.
   *
   * @param next the new status
   * @return the copy
   */
  public ValveCommandView withStatus(String next) {
    return new ValveCommandView(
        id, aqueductId, deviceId, valveId, command, next, notBefore, expiresAt);
  }
}
