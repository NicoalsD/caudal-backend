package co.caudal.application.autopilot;

import co.caudal.application.event.DomainEventSubscriber;
import co.caudal.domain.event.OperatorActionRecorded;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, in memory, the last time a person acted on the valves of each aqueduct. Any manual
 * action pauses the autopilot (HU-42): the cycle takes the latest of this memory and of the
 * database, so a pause is respected at once even before the next read of the database.
 *
 * @pattern P17 Observer
 */
public class OperatorPresenceTracker implements DomainEventSubscriber<OperatorActionRecorded> {

  private final Map<UUID, Instant> lastAction = new ConcurrentHashMap<>();

  @Override
  public Class<OperatorActionRecorded> eventType() {
    return OperatorActionRecorded.class;
  }

  @Override
  public void on(OperatorActionRecorded event) {
    lastAction.merge(event.aqueductId(), event.occurredAt(), (a, b) -> a.isAfter(b) ? a : b);
  }

  /**
   * The last human action seen by this instance.
   *
   * @param aqueductId the aqueduct
   * @return the moment, or empty
   */
  public Optional<Instant> lastActionAt(UUID aqueductId) {
    return Optional.ofNullable(lastAction.get(aqueductId));
  }
}
