package co.caudal.application.event;

import co.caudal.domain.event.DomainEvent;

/**
 * Hands a domain event to every subscriber of its type, in the transaction of the publisher: if a
 * subscriber fails, the change that raised the event is undone too.
 *
 * @pattern P17 Observer
 */
public interface DomainEventPublisher {

  /**
   * Publishes an event.
   *
   * @param event the event
   */
  void publish(DomainEvent event);
}
