package co.caudal.application.event;

import co.caudal.domain.event.DomainEvent;
import java.util.List;
import java.util.Objects;

/**
 * Delivers each event to the subscribers of its type, one after the other, in the order they were
 * registered.
 *
 * @pattern P17 Observer
 */
public final class SimpleDomainEventPublisher implements DomainEventPublisher {

  private final List<DomainEventSubscriber<? extends DomainEvent>> subscribers;

  /**
   * Creates the publisher.
   *
   * @param subscribers every subscriber
   */
  public SimpleDomainEventPublisher(
      List<? extends DomainEventSubscriber<? extends DomainEvent>> subscribers) {
    this.subscribers = List.copyOf(Objects.requireNonNull(subscribers, "subscribers"));
  }

  @Override
  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    for (DomainEventSubscriber<? extends DomainEvent> subscriber : subscribers) {
      deliver(subscriber, event);
    }
  }

  private static <E extends DomainEvent> void deliver(
      DomainEventSubscriber<E> subscriber, DomainEvent event) {
    if (subscriber.eventType().isInstance(event)) {
      subscriber.on(subscriber.eventType().cast(event));
    }
  }
}
