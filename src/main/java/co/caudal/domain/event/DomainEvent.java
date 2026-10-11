package co.caudal.domain.event;

import java.time.Instant;

/**
 * Something that happened in the domain that other parts of the system may react to.
 *
 * @pattern P17 Observer
 */
public interface DomainEvent {

  /**
   * When it happened.
   *
   * @return the moment
   */
  Instant occurredAt();
}
