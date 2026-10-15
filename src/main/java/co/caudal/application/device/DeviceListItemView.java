package co.caudal.application.device;

import java.time.Instant;
import java.util.UUID;

/**
 * A device as the list shows it: its own columns and the key that is valid now, if any. It never
 * carries the public key or the fingerprint.
 *
 * @param id the device
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name the name
 * @param tankId the tank a level sensor measures, or null
 * @param valveId the valve a valve actuator moves, or null
 * @param status ACTIVE, SUSPENDED or RETIRED
 * @param createdAt when the device was registered
 * @param lastSeenAt last contact, or null if it never reported
 * @param activeKey the key that is valid now, or null if the device has none
 */
public record DeviceListItemView(
    UUID id,
    String kind,
    String name,
    UUID tankId,
    UUID valveId,
    String status,
    Instant createdAt,
    Instant lastSeenAt,
    DeviceKeyValidityView activeKey) {}
