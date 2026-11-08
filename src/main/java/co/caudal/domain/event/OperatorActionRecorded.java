package co.caudal.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A person acted on the valves: sent a manual command, or accepted or rejected a decision of the
 * autopilot. Any of these pauses the autopilot for {@code operator_absence_minutes}.
 *
 * @param aqueductId the aqueduct
 * @param userId the person
 * @param occurredAt when it happened
 */
public record OperatorActionRecorded(UUID aqueductId, UUID userId, Instant occurredAt)
    implements DomainEvent {}
