package co.caudal.application.port.out;

import co.caudal.application.device.DeviceKeyView;
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

  /**
   * Revokes the active key of a device with a grace period: it stays valid for the given days. Does
   * nothing if the device has no active key.
   *
   * @param deviceId the device
   * @param graceDays days the key stays valid, counted from now
   */
  void retireActiveKey(UUID deviceId, int graceDays);

  /**
   * Registers a key of a device unless the same key is already registered (on any device).
   *
   * @param deviceId the device
   * @param aqueductId the aqueduct of the device
   * @param publicKey the key in base64url without padding
   * @param fingerprint SHA-256 of the key
   * @return the key, or empty if the key already exists
   */
  Optional<DeviceKeyView> insertKey(
      UUID deviceId, UUID aqueductId, String publicKey, String fingerprint);

  /**
   * Revokes a key now, if it is valid (not revoked, or revoked in the future).
   *
   * @param deviceId the device
   * @param keyId the key
   * @return the key, or empty if it is unknown for the device or already revoked
   */
  Optional<DeviceKeyView> revokeKey(UUID deviceId, UUID keyId);

  /**
   * Tells whether a device has a key, revoked or not.
   *
   * @param deviceId the device
   * @param keyId the key
   * @return true if the key belongs to the device
   */
  boolean hasKey(UUID deviceId, UUID keyId);
}
