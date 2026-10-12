package co.caudal.application.port.out;

import co.caudal.application.device.command.NewValveCommand;
import co.caudal.application.device.command.ValveCommandView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the valve commands of the devices and their history. Runs inside a transaction
 * opened with {@link UnitOfWorkPort#executeAs}: the tables have row level security.
 */
public interface ValveCommandPort {

  /**
   * Lists the commands of a device that are waiting for it or delivered and not confirmed, by
   * {@code not_before}.
   *
   * @param deviceId the device
   * @return the commands, possibly empty
   */
  List<ValveCommandView> open(UUID deviceId);

  /**
   * Finds one command of a device.
   *
   * @param commandId the command
   * @param deviceId the device that must receive it; a command of another device is not found
   * @return the command, or empty
   */
  Optional<ValveCommandView> find(UUID commandId, UUID deviceId);

  /**
   * Moves a command to a new status only if it still has the expected one (compare and set), so two
   * requests cannot both make the same transition.
   *
   * @param commandId the command
   * @param from the status the command must have
   * @param to the new status
   * @return true if the status changed
   */
  boolean changeStatus(UUID commandId, String from, String to);

  /**
   * Records a transition of a command in its history.
   *
   * @param aqueductId the aqueduct of the command
   * @param commandId the command
   * @param event the event, which is the status reached
   * @param detail the detail sent by the device, or null
   * @param occurredAt when it happened
   */
  void insertEvent(
      UUID aqueductId, UUID commandId, String event, String detail, Instant occurredAt);

  /**
   * Stores a new command in {@code PENDING}. A command of the same valve with the same {@code
   * not_before} that is still PENDING or DELIVERED is not stored
   * (docs/Protocolo-de-dispositivos.md, section 5.5).
   *
   * @param command the command to store
   * @return the stored command, or empty if an active command of the valve already has that moment
   */
  Optional<ValveCommandView> insert(NewValveCommand command);
}
