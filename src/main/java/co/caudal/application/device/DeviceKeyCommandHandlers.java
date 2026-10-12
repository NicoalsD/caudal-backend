package co.caudal.application.device;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.DevicePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.device.DevicePublicKey;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/** Handlers of the public keys of the devices (docs/API.md, sections 16.5 and 16.8). */
public final class DeviceKeyCommandHandlers {

  private DeviceKeyCommandHandlers() {}

  /**
   * Registers a key and rotates the previous one: in one transaction the active key is revoked
   * {@code keyRotationDays} days ahead, then the new key is inserted. The same key registered
   * before, on any device, is a conflict and rolls the rotation back.
   *
   * @pattern P13 Command
   */
  public static final class Rotate
      implements CommandHandler<RotateDeviceKeyCommand, DeviceKeyView> {

    private final DevicePort devices;
    private final UnitOfWorkPort unitOfWork;
    private final int keyRotationDays;

    /**
     * Creates the handler.
     *
     * @param devices the devices
     * @param unitOfWork transaction boundary
     * @param keyRotationDays days the previous key stays valid
     */
    public Rotate(DevicePort devices, UnitOfWorkPort unitOfWork, int keyRotationDays) {
      this.devices = devices;
      this.unitOfWork = unitOfWork;
      this.keyRotationDays = keyRotationDays;
    }

    @Override
    public DeviceKeyView handle(RotateDeviceKeyCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            DeviceView device =
                devices
                    .device(command.deviceId())
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            DevicePublicKey key =
                DevicePublicKey.parse(command.publicKey())
                    .orElseThrow(() -> new DomainException(ErrorCode.INVALID_KEY));
            devices.retireActiveKey(device.id(), keyRotationDays);
            return devices
                .insertKey(device.id(), device.aqueductId(), key.encoded(), key.fingerprint())
                .orElseThrow(
                    () -> new DomainException(ErrorCode.CONFLICT, Map.of("field", "public_key")));
          });
    }

    @Override
    public Class<RotateDeviceKeyCommand> commandType() {
      return RotateDeviceKeyCommand.class;
    }
  }

  /**
   * Revokes a key now. A key already revoked is a conflict; an unknown key of the device is not
   * found.
   *
   * @pattern P13 Command
   */
  public static final class Revoke
      implements CommandHandler<RevokeDeviceKeyCommand, DeviceKeyView> {

    private final DevicePort devices;
    private final UnitOfWorkPort unitOfWork;

    /**
     * Creates the handler.
     *
     * @param devices the devices
     * @param unitOfWork transaction boundary
     */
    public Revoke(DevicePort devices, UnitOfWorkPort unitOfWork) {
      this.devices = devices;
      this.unitOfWork = unitOfWork;
    }

    @Override
    public DeviceKeyView handle(RevokeDeviceKeyCommand command) {
      return unitOfWork.executeAs(
          command.caller().scope(),
          () -> {
            devices
                .device(command.deviceId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            return devices
                .revokeKey(command.deviceId(), command.keyId())
                .orElseThrow(
                    () ->
                        devices.hasKey(command.deviceId(), command.keyId())
                            ? new DomainException(ErrorCode.CONFLICT)
                            : new DomainException(ErrorCode.NOT_FOUND));
          });
    }

    @Override
    public Class<RevokeDeviceKeyCommand> commandType() {
      return RevokeDeviceKeyCommand.class;
    }
  }
}
