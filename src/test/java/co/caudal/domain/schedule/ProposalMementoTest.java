package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProposalMementoTest {

  static final Instant START = Instant.parse("2026-10-09T10:00:00Z");

  static ScheduleItemSnapshot item(int sequence) {
    return new ScheduleItemSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        sequence,
        START.plusSeconds(3600L * sequence),
        START.plusSeconds(3600L * (sequence + 1)),
        "PROPOSED",
        List.of(new TurnReason("PRIORITY_SECTOR", Map.of("sector", "Alto"))));
  }

  @Test
  void theSnapshotDoesNotFollowLaterChangesOfTheList() {
    List<ScheduleItemSnapshot> items = new ArrayList<>(List.of(item(1)));
    ProposalMemento memento =
        new ProposalMemento(UUID.randomUUID(), 0, "PRIORITY_THEN_LONGEST_WAIT", items, START);

    items.add(item(2));

    assertThat(memento.items()).hasSize(1);
    assertThatThrownBy(() -> memento.items().add(item(3)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void restoreGivesBackTheOriginalShifts() {
    ScheduleItemSnapshot first = item(1);
    ScheduleItemSnapshot second = item(2);
    ProposalMemento memento =
        new ProposalMemento(UUID.randomUUID(), 2, "EQUAL_SPLIT", List.of(first, second), START);

    List<ProposedTurn> turns = memento.restore();

    assertThat(turns)
        .extracting(ProposedTurn::sectorId, ProposedTurn::sequence, ProposedTurn::start)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(first.sectorId(), 1, first.start()),
            org.assertj.core.groups.Tuple.tuple(second.sectorId(), 2, second.start()));
    assertThat(turns.getFirst().reasons()).isEqualTo(first.reasons());
    assertThat(memento.lockVersion()).isEqualTo(2);
  }
}
