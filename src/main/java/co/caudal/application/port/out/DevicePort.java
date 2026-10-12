package co.caudal.application.port.out;

import co.caudal.application.device.DeviceKeyView;
import co.caudal.application.device.DeviceView;
import co.caudal.application.device.auth.DeviceCredentials;
import co.caudal.domain.device.DeviceKind;
import co.caudal.domain.security.SecurityEventType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the field devices. Runs inside a transaction opened with {@link
 * UnitOfWorkPort#executeAs}: the tables have row level security.
 */
public interface DevicePort {

  /**
   * Reads what the signature check needs about a device, whatever its aqueduct: the device and the
   * keys that are still valid. It goes through a function with definer rights, because the device
   * is not yet known to belong to the aqueduct of the session.
   *
   * @param deviceId the device
   * @return the credentials, or empty if the device does not exist
   */
  Optional<DeviceCredentials> credentials(UUID deviceId);

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

  /**
   * Counts the security events of one type recorded for a device since a moment. Used to detect the
   * repeated invalid signatures of a device.
   *
   * @param deviceId the device
   * @param type the type of event
   * @param since the start of the count, inclusive
   * @return the number of events
   */
  long countSecurityEvents(UUID deviceId, SecurityEventType type, Instant since);

  /**
   * Tells whether a nonce was stored for a device at or after a moment (step 4: it only looks).
   *
   * @param deviceId the device
   * @param nonce the nonce, 32 hexadecimal characters
   * @param since the start of the lifetime, inclusive
   * @return true if the nonce is still within its lifetime
   */
  boolean nonceSeenSince(UUID deviceId, String nonce, Instant since);

  /**
   * Stores a nonce of a device unless it is already stored (step 5b). The nonce table has no
   * lifetime of its own, so a stored nonce is never stored again, whatever its age.
   *
   * @param deviceId the device
   * @param nonce the nonce
   * @return true if stored now, false if the nonce was already there
   */
  boolean insertNonce(UUID deviceId, String nonce);
}
