package co.caudal.application.device.command;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.device.DeviceView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.ValveCommandPort;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;
import java.util.Objects;

/**
 * Stores a manual valve command as {@code PENDING} (docs/API.md, section 16.6). The valve must
 * belong to the aqueduct of the session, be motorized and have an active actuator; otherwise it is
 * not found. Two active commands of the valve with the same {@code not_before} are a conflict
 * (docs/Protocolo-de-dispositivos.md, section 5.5).
 *
 * @pattern P13 Command
 */
public final class ManualValveCommandHandler
    implements CommandHandler<CreateManualValveCommand, ValveCommandView> {

  private static final String FIELD = "field";

  private final DevicePort devices;
  private final NetworkPort network;
  private final ValveCommandPort commands;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param devices the actuators of the valves
   * @param network the valves of the aqueduct
   * @param commands the valve commands
   * @param unitOfWork transaction boundary
   */
  public ManualValveCommandHandler(
      DevicePort devices,
      NetworkPort network,
      ValveCommandPort commands,
      UnitOfWorkPort unitOfWork) {
    this.devices = devices;
    this.network = network;
    this.commands = commands;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ValveCommandView handle(CreateManualValveCommand command) {
    ChangeReason reason = ChangeReason.require(command.manualReason());
    Objects.requireNonNull(command.notBefore(), "notBefore");
    Objects.requireNonNull(command.expiresAt(), "expiresAt");
    if (!command.expiresAt().isAfter(command.notBefore())) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of(FIELD, "expires_at"));
    }
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ValveView valve =
              network
                  .valve(command.valveId())
                  .filter(ValveView::motorized)
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          DeviceView actuator =
              devices
                  .activeActuatorOf(valve.id())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return commands
              .insert(
                  new NewValveCommand(
                      command.caller().aqueductId(),
                      actuator.id(),
                      valve.id(),
                      command.command(),
                      null,
                      reason.value(),
                      command.caller().userId(),
                      command.notBefore(),
                      command.expiresAt()))
              .orElseThrow(
                  () -> new DomainException(ErrorCode.CONFLICT, Map.of(FIELD, "not_before")));
        });
  }

  @Override
  public Class<CreateManualValveCommand> commandType() {
    return CreateManualValveCommand.class;
  }
}
