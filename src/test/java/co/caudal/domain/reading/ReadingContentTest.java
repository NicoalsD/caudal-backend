package co.caudal.domain.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingContentTest {

  private static final UUID TANK = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-11T18:00:00.123456789Z");

  private static ReadingContent content(String value, String note, Instant at) {
    return new ReadingContent(TANK, new BigDecimal(value), "NORMAL", false, note, at);
  }

  @Test
  void theSameReportSentAgainIsTheSameReading() {
    assertThat(content("2.1", null, AT).sameAs(content("2.10", null, AT.minusNanos(789)))).isTrue();
  }

  @Test
  void anyDifferenceMakesItAnotherReading() {
    ReadingContent base = content("2.10", "nota", AT);

    assertThat(base.sameAs(content("2.15", "nota", AT))).isFalse();
    assertThat(base.sameAs(content("2.10", "otra", AT))).isFalse();
    assertThat(base.sameAs(content("2.10", "nota", AT.plusSeconds(1)))).isFalse();
    assertThat(base.sameAs(content("2.10", "nota", null))).isFalse();
    assertThat(
            base.sameAs(
                new ReadingContent(TANK, new BigDecimal("2.10"), "MUDDY", false, "nota", AT)))
        .isFalse();
    assertThat(
            base.sameAs(
                new ReadingContent(TANK, new BigDecimal("2.10"), "NORMAL", true, "nota", AT)))
        .isFalse();
    assertThat(
            base.sameAs(
                new ReadingContent(
                    UUID.randomUUID(), new BigDecimal("2.10"), "NORMAL", false, "nota", AT)))
        .isFalse();
  }
}
