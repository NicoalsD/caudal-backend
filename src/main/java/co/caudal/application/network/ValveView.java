package co.caudal.application.network;

import java.util.UUID;

/**
 * A physical valve.
 *
 * @param id the valve
 * @param code the code, unique per sector
 * @param name the name
 * @param locationHint reference of location, or null
 * @param motorized true if it has an actuator
 * @param failSafePosition KEEP, OPEN or CLOSED
 * @param active true if in use
 */
public record ValveView(
    UUID id,
    String code,
    String name,
    String locationHint,
    boolean motorized,
    String failSafePosition,
    boolean active) {}
