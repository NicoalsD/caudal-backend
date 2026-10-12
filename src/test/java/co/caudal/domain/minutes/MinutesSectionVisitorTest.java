package co.caudal.domain.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinutesSectionVisitorTest {

  static final MinutesEntry ENTRY =
      new MinutesEntry("reading", "1", Instant.parse("2026-10-02T10:00:00Z"), "Lectura");

  @Test
  void eachRecordFallsInItsSection() {
    SectionClassifier classifier = new SectionClassifier();

    assertThat(new ObservedRecord(ENTRY).accept(classifier)).isEqualTo(MinutesSection.OBSERVED);
    assertThat(new EstimatedRecord(ENTRY).accept(classifier)).isEqualTo(MinutesSection.ESTIMATED);
    assertThat(new InferredRecord(ENTRY).accept(classifier)).isEqualTo(MinutesSection.INFERRED);
    assertThat(new ConfirmedRecord(ENTRY).accept(classifier)).isEqualTo(MinutesSection.CONFIRMED);
  }

  @Test
  void theBuilderPlacesRecordsWithTheVisitor() {
    List<EpistemicRecord> records =
        List.of(
            new ObservedRecord(ENTRY),
            new InferredRecord(ENTRY),
            new ConfirmedRecord(ENTRY),
            new ConfirmedRecord(ENTRY));
    MinutesBuilder builder =
        new MinutesBuilder().period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
    records.forEach(builder::add);

    Minutes minutes = builder.build();

    assertThat(minutes.section(MinutesSection.OBSERVED)).hasSize(1);
    assertThat(minutes.section(MinutesSection.ESTIMATED)).isEmpty();
    assertThat(minutes.section(MinutesSection.INFERRED)).hasSize(1);
    assertThat(minutes.section(MinutesSection.CONFIRMED)).hasSize(2);
  }

  @Test
  void aVisitorCanComputeAnythingOverTheKinds() {
    MinutesSectionVisitor<String> labels =
        new MinutesSectionVisitor<>() {
          @Override
          public String visitObserved(ObservedRecord record) {
            return "visto: " + record.entry().summary();
          }

          @Override
          public String visitEstimated(EstimatedRecord record) {
            return "calculado";
          }

          @Override
          public String visitInferred(InferredRecord record) {
            return "sospechado";
          }

          @Override
          public String visitConfirmed(ConfirmedRecord record) {
            return "verificado";
          }
        };

    assertThat(new ObservedRecord(ENTRY).accept(labels)).isEqualTo("visto: Lectura");
    assertThat(new EstimatedRecord(ENTRY).accept(labels)).isEqualTo("calculado");
  }
}
