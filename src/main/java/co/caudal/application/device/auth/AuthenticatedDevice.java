package co.caudal.application.device.auth;

import java.util.UUID;

/**
 * A device whose signed request passed every check of docs/Protocolo-de-dispositivos.md, section
 * 3.1.
 *
 * @param deviceId the device
 * @param aqueductId the aqueduct of the device, which scopes the rest of the work
 * @param kind the kind of device
 * @param tankId the tank of a level sensor, or null
 * @param valveId the valve of a valve actuator, or null
 * @param keyId the key that verified the signature
 */
public record AuthenticatedDevice(
    UUID deviceId, UUID aqueductId, String kind, UUID tankId, UUID valveId, UUID keyId) {}
