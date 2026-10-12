package co.caudal.application.device.command;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.Instant;
import java.util.UUID;

/**
 * Sends a manual command to a valve: a person opens or closes it, with a reason (docs/API.md,
 * section 16.6; docs/Protocolo-de-dispositivos.md, section 5.1).
 *
 * @param caller the board administrator who sends it
 * @param valveId the valve, which must be motorized and have an active actuator
 * @param command OPEN or CLOSE
 * @param manualReason why, 10 to 500 characters, checked by the handler
 * @param notBefore the command must not run before this moment
 * @param expiresAt the command must not run from this moment on, after {@code notBefore}
 */
public record CreateManualValveCommand(
    Caller caller,
    UUID valveId,
    String command,
    String manualReason,
    Instant notBefore,
    Instant expiresAt)
    implements AuditableCommand<ValveCommandView> {

  @Override
  public String auditEntityType() {
    return "valve_command";
  }

  @Override
  public String auditEntityId(ValveCommandView result) {
    return result.id().toString();
  }
}
