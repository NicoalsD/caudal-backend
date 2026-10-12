package co.caudal.application.port.out;

import co.caudal.application.device.DeviceView;
import co.caudal.domain.device.DeviceKind;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the field devices. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the tables have row level security.
 */
public interface DevicePort {

  /**
   * Finds a device of the aqueduct of the session.
   *
   * @param id the device
   * @return the device, or empty if it is not in the aqueduct of the session
   */
  Optional<DeviceView> device(UUID id);

  /**
   * Registers a device. It starts ACTIVE and without keys.
   *
   * @param aqueductId the aqueduct of the session
   * @param kind the kind of device
   * @param name the name
   * @param tankId the tank of a level sensor, or null
   * @param valveId the valve of a valve actuator, or null
   * @param firmwareVersion firmware version, or null
   * @param createdBy the account that registers it
   * @return the device
   */
  DeviceView insertDevice(
      UUID aqueductId,
      DeviceKind kind,
      String name,
      UUID tankId,
      UUID valveId,
      String firmwareVersion,
      UUID createdBy);

  /**
   * Changes the status of a device.
   *
   * @param id the device
   * @param status ACTIVE, SUSPENDED or RETIRED
   * @return the device
   */
  DeviceView updateStatus(UUID id, String status);
}
