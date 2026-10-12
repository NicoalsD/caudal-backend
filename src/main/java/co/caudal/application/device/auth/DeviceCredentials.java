package co.caudal.application.device.auth;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the signature check needs to know about a device before the signature is verified: its
 * aqueduct, its status and its keys that are still valid (docs/Protocolo-de-dispositivos.md,
 * section 3.1, steps 1 and 2).
 *
 * @param deviceId the device
 * @param aqueductId the aqueduct of the device
 * @param status ACTIVE, SUSPENDED or RETIRED
 * @param kind the kind of device
 * @param tankId the tank of a level sensor, or null
 * @param valveId the valve of a valve actuator, or null
 * @param keys the keys that may verify signatures, empty if there is none
 */
public record DeviceCredentials(
    UUID deviceId,
    UUID aqueductId,
    String status,
    String kind,
    UUID tankId,
    UUID valveId,
    List<DeviceKeyCredential> keys) {

  /** Validates the required parts and copies the keys. */
  public DeviceCredentials {
    Objects.requireNonNull(deviceId, "deviceId");
    Objects.requireNonNull(aqueductId, "aqueductId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(kind, "kind");
    keys = List.copyOf(keys);
  }
}
