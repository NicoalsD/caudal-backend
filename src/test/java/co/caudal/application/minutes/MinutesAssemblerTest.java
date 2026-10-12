package co.caudal.application.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.minutes.ConfirmedRecord;
import co.caudal.domain.minutes.EstimatedRecord;
import co.caudal.domain.minutes.InferredRecord;
import co.caudal.domain.minutes.Minutes;
import co.caudal.domain.minutes.MinutesSection;
import co.caudal.domain.minutes.ObservedRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MinutesAssemblerTest {

  static final Instant AT = Instant.parse("2026-10-02T10:00:00Z");

  static MinutesFact fact(String source, String status, Map<String, String> values) {
    return new MinutesFact(source, "1", AT, status, values);
  }

  @Test
  void readingsAreObservedUnlessFlagged() {
    assertThat(MinutesAssembler.classify(fact("reading", "ACCEPTED", Map.of("valor", "2,10"))))
        .isInstanceOf(ObservedRecord.class);
    assertThat(MinutesAssembler.classify(fact("reading", "FLAGGED", Map.of("valor", "2,10"))))
        .isInstanceOf(InferredRecord.class);
  }

  @Test
  void incidentsGoByHowSureTheyAre() {
    Map<String, String> leak = Map.of("categoria", "Fuga");
    assertThat(MinutesAssembler.classify(fact("incident", "REPORTED", leak)))
        .isInstanceOf(ObservedRecord.class);
    assertThat(MinutesAssembler.classify(fact("incident", "VERIFYING", leak)))
        .isInstanceOf(ObservedRecord.class);
    assertThat(MinutesAssembler.classify(fact("incident", "RESOLVED", leak)))
        .isInstanceOf(ConfirmedRecord.class);
    assertThat(MinutesAssembler.classify(fact("incident", "DISMISSED", leak)))
        .isInstanceOf(InferredRecord.class);
  }

  @Test
  void aConfirmedAnomalyIsConfirmedOnlyWithAnIncidentCheckedInTheField() {
    assertThat(
            MinutesAssembler.classify(
                fact(
                    "anomaly",
                    "CONFIRMED",
                    Map.of("tipo", "POSSIBLE_LEAK", "incidente", "CONFIRMED"))))
        .isInstanceOf(ConfirmedRecord.class);
    assertThat(
            MinutesAssembler.classify(
                fact("anomaly", "CONFIRMED", Map.of("tipo", "POSSIBLE_LEAK"))))
        .isInstanceOf(InferredRecord.class);
    assertThat(MinutesAssembler.classify(fact("anomaly", "OPEN", Map.of("tipo", "STALE_DATA"))))
        .isInstanceOf(InferredRecord.class);
  }

  @Test
  void calculationsAreEstimatedAndExecutionsConfirmed() {
    assertThat(
            MinutesAssembler.classify(
                fact("forecast", null, Map.of("modelo", "chronos", "dias", "3", "respaldo", ""))))
        .isInstanceOf(EstimatedRecord.class);
    Minutes minutes =
        MinutesAssembler.assemble(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 7),
            List.of(
                fact("execution", "COMPLETED", Map.of("sector", "Alto", "dia", "2026-10-02")),
                fact("correction", null, Map.of("valor", "2,15", "motivo", "Se leyó mal."))));

    assertThat(minutes.section(MinutesSection.CONFIRMED).getFirst().summary())
        .isEqualTo("Turno de Alto del 2026-10-02: COMPLETED.");
    assertThat(minutes.section(MinutesSection.OBSERVED).getFirst().summary())
        .isEqualTo("Corrección de una lectura a 2,15. Motivo: Se leyó mal.");
  }
}
