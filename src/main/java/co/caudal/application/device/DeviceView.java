package co.caudal.application.device;

import java.time.Instant;
import java.util.UUID;

/**
 * A field device of an aqueduct.
 *
 * @param id the device
 * @param aqueductId the aqueduct that owns it
 * @param kind LEVEL_SENSOR, VALVE_ACTUATOR or GATEWAY
 * @param name the name
 * @param tankId the tank a level sensor measures, or null
 * @param valveId the valve a valve actuator moves, or null
 * @param status ACTIVE, SUSPENDED or RETIRED
 * @param firmwareVersion firmware version, or null
 * @param lastSeenAt last contact, or null if it never reported
 */
public record DeviceView(
    UUID id,
    UUID aqueductId,
    String kind,
    String name,
    UUID tankId,
    UUID valveId,
    String status,
    String firmwareVersion,
    Instant lastSeenAt) {}
