package co.caudal.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.event.DomainEvent;
import co.caudal.domain.event.RuleSetActivated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleDomainEventPublisherTest {

  record OtherEvent(Instant occurredAt) implements DomainEvent {}

  static final class Recorder<E extends DomainEvent> implements DomainEventSubscriber<E> {
    private final Class<E> type;
    final List<E> received = new ArrayList<>();

    Recorder(Class<E> type) {
      this.type = type;
    }

    @Override
    public Class<E> eventType() {
      return type;
    }

    @Override
    public void on(E event) {
      received.add(event);
    }
  }

  private static final RuleSetActivated ACTIVATED =
      new RuleSetActivated(
          UUID.randomUUID(),
          UUID.randomUUID(),
          2,
          null,
          UUID.randomUUID(),
          "Motivo largo.",
          Instant.EPOCH);

  @Test
  void eachSubscriberGetsOnlyItsTypeInOrder() {
    Recorder<RuleSetActivated> first = new Recorder<>(RuleSetActivated.class);
    Recorder<RuleSetActivated> second = new Recorder<>(RuleSetActivated.class);
    Recorder<OtherEvent> other = new Recorder<>(OtherEvent.class);
    DomainEventPublisher publisher = new SimpleDomainEventPublisher(List.of(first, other, second));

    publisher.publish(ACTIVATED);
    publisher.publish(new OtherEvent(Instant.EPOCH));

    assertThat(first.received).containsExactly(ACTIVATED);
    assertThat(second.received).containsExactly(ACTIVATED);
    assertThat(other.received).hasSize(1);
  }

  @Test
  void aFailingSubscriberStopsThePublication() {
    DomainEventSubscriber<RuleSetActivated> failing =
        new DomainEventSubscriber<>() {
          @Override
          public Class<RuleSetActivated> eventType() {
            return RuleSetActivated.class;
          }

          @Override
          public void on(RuleSetActivated event) {
            throw new IllegalStateException("broken");
          }
        };
    DomainEventPublisher publisher = new SimpleDomainEventPublisher(List.of(failing));

    assertThatThrownBy(() -> publisher.publish(ACTIVATED))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(NullPointerException.class);
  }
}
