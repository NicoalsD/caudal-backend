package co.caudal.application.device;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.device.DeviceKind;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.util.UUID;

/** Handlers that register and change field devices (docs/API.md, section 16.4 and 16.7). */
public final class DeviceCommandHandlers {

  private static final String FIELD_REQUIRED = "FIELD_REQUIRED";
  private static final String FIELD_VALUE_NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private DeviceCommandHandlers() {}

  /**
   * Registers a device. A level sensor names an active tank of the aqueduct, a valve actuator a
   * valve of the aqueduct, and the other references must be empty.
   *
   * @pattern P13 Command
   */
  public static final class Create implements CommandHandler<CreateDeviceCommand, DeviceView> {

    private final DevicePort devices;
    private final NetworkPort network;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param devices the devices
     * @param network the network, to check the references
     * @param unitOfWork transaction boundary
     */
    public Create(DevicePort devices, NetworkPort network, UnitOfWorkPort unitOfWork) {
      this.devices = devices;
      this.network = network;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public DeviceView handle(CreateDeviceCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            DeviceKind kind = DeviceKind.valueOf(command.kind());
            checkReferences(kind, command.tankId(), command.valveId(), network);
            return devices.insertDevice(
                command.caller().aqueductId(),
                kind,
                command.name().strip(),
                command.tankId(),
                command.valveId(),
                command.firmwareVersion() == null || command.firmwareVersion().isBlank()
                    ? null
                    : command.firmwareVersion().strip(),
                command.caller().userId());
          });
    }

    @Override
    public Class<CreateDeviceCommand> commandType() {
      return CreateDeviceCommand.class;
    }
  }

  /**
   * Changes the status of a device of the aqueduct. Any status may follow any other.
   *
   * @pattern P13 Command
   */
  public static final class UpdateStatus
      implements CommandHandler<UpdateDeviceCommand, DeviceView> {

    private final DevicePort devices;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param devices the devices
     * @param unitOfWork transaction boundary
     */
    public UpdateStatus(DevicePort devices, UnitOfWorkPort unitOfWork) {
      this.devices = devices;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public DeviceView handle(UpdateDeviceCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            devices
                .device(command.deviceId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            return devices.updateStatus(command.deviceId(), command.status());
          });
    }

    @Override
    public Class<UpdateDeviceCommand> commandType() {
      return UpdateDeviceCommand.class;
    }
  }

  /** Checks that the references match the kind; a missing or foreign one is a field error. */
  private static void checkReferences(
      DeviceKind kind, UUID tankId, UUID valveId, NetworkPort network) {
    if (kind.tankRequired()) {
      if (tankId == null) {
        throw new FieldValidationException("tank_id", FIELD_REQUIRED);
      }
      if (network.tank(tankId).filter(TankView::active).isEmpty()) {
        throw new FieldValidationException("tank_id", FIELD_VALUE_NOT_ALLOWED);
      }
    } else if (tankId != null) {
      throw new FieldValidationException("tank_id", FIELD_VALUE_NOT_ALLOWED);
    }
    if (kind.valveRequired()) {
      if (valveId == null) {
        throw new FieldValidationException("valve_id", FIELD_REQUIRED);
      }
      if (network.valve(valveId).isEmpty()) {
        throw new FieldValidationException("valve_id", FIELD_VALUE_NOT_ALLOWED);
      }
    } else if (valveId != null) {
      throw new FieldValidationException("valve_id", FIELD_VALUE_NOT_ALLOWED);
    }
  }
}
