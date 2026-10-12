package co.caudal.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The device confirmed a valve command as done or failed (docs/Protocolo-de-dispositivos.md,
 * section 7).
 *
 * @param aqueductId the aqueduct of the command
 * @param commandId the command
 * @param deviceId the actuator that confirmed it
 * @param result ACKED or FAILED
 * @param occurredAt when it was confirmed
 */
public record ValveCommandConfirmed(
    UUID aqueductId, UUID commandId, UUID deviceId, String result, Instant occurredAt)
    implements DomainEvent {}
