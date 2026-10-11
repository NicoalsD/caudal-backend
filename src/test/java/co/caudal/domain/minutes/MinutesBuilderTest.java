package co.caudal.domain.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MinutesBuilderTest {

  static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  static final LocalDate TO = LocalDate.of(2026, 10, 7);

  static MinutesEntry entry(String id, String at) {
    return new MinutesEntry("reading", id, Instant.parse(at), "Lectura " + id);
  }

  @Test
  void theFourSectionsAlwaysExistAndComeInTimeOrder() {
    Minutes minutes =
        new MinutesBuilder()
            .period(FROM, TO)
            .add(MinutesSection.OBSERVED, entry("b", "2026-10-03T10:00:00Z"))
            .add(MinutesSection.OBSERVED, entry("a", "2026-10-02T10:00:00Z"))
            .build();

    assertThat(minutes.sections()).containsOnlyKeys(MinutesSection.values());
    assertThat(minutes.section(MinutesSection.OBSERVED))
        .extracting(MinutesEntry::sourceId)
        .containsExactly("a", "b");
    assertThat(minutes.section(MinutesSection.CONFIRMED)).isEmpty();
    assertThat(minutes.periodFrom()).isEqualTo(FROM);
  }

  @Test
  void thePeriodIsRequiredAndNotInverted() {
    assertThatThrownBy(() -> new MinutesBuilder().build())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new MinutesBuilder().period(TO, FROM).build())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theResultDoesNotChangeWithTheBuilder() {
    MinutesBuilder builder = new MinutesBuilder().period(FROM, TO);
    Minutes first = builder.build();

    builder.add(MinutesSection.INFERRED, entry("c", "2026-10-04T10:00:00Z"));

    assertThat(first.section(MinutesSection.INFERRED)).isEmpty();
    assertThatThrownBy(
            () -> first.section(MinutesSection.INFERRED).add(entry("d", "2026-10-04T10:00:00Z")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(MinutesSection.OBSERVED.key()).isEqualTo("observed");
    assertThat(MinutesSection.CONFIRMED.title()).isEqualTo("Confirmado");
  }
}
