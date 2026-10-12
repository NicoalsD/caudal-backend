package co.caudal.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A valve command went to its device for the first time (docs/Protocolo-de-dispositivos.md, section
 * 7).
 *
 * @param aqueductId the aqueduct of the command
 * @param commandId the command
 * @param deviceId the actuator that received it
 * @param occurredAt when it was delivered
 */
public record ValveCommandDelivered(
    UUID aqueductId, UUID commandId, UUID deviceId, Instant occurredAt) implements DomainEvent {}
