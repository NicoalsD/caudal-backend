package co.caudal.application.event;

import co.caudal.domain.event.DomainEvent;

/**
 * Reacts to one type of domain event.
 *
 * @param <E> type of the event
 * @pattern P17 Observer
 */
public interface DomainEventSubscriber<E extends DomainEvent> {

  /**
   * The type of event it listens to.
   *
   * @return the class of the event
   */
  Class<E> eventType();

  /**
   * Reacts to an event.
   *
   * @param event the event
   */
  void on(E event);
}
