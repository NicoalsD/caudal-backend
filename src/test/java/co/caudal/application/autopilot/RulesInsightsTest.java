package co.caudal.application.autopilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RulesInsightsTest {

  private static final Instant NOW = Instant.parse("2026-11-07T15:00:00Z");
  private static final UUID SECTOR = UUID.randomUUID();
  private static final UUID VALVE = UUID.randomUUID();

  private static InsightsSnapshot snapshot(
      String level,
      String state,
      List<InsightsSnapshot.Shift> shifts,
      List<InsightsSnapshot.Anomaly> anomalies) {
    return new InsightsSnapshot(
        UUID.randomUUID(),
        "Vereda de Prueba",
        ZoneId.of("America/Bogota"),
        NOW,
        "ASSISTED",
        new BigDecimal("25"),
        null,
        new InsightsSnapshot.Tank(
            UUID.randomUUID(),
            "Tanque",
            level == null ? null : new BigDecimal("2"),
            new BigDecimal("5"),
            level == null ? null : new BigDecimal(level),
            "LOW",
            NOW,
            "FALLING"),
        List.of(
            new InsightsSnapshot.ForecastPoint(
                NOW, new BigDecimal("1.5"), new BigDecimal("2"), new BigDecimal("2.5"))),
        List.of(new InsightsSnapshot.Valve(VALVE, SECTOR, "Alto", "Norte", state, true, 1)),
        shifts,
        anomalies);
  }

  private static InsightsSnapshot.Shift shift(long fromSeconds, long toSeconds) {
    return new InsightsSnapshot.Shift(
        SECTOR, NOW.plusSeconds(fromSeconds), NOW.plusSeconds(toSeconds), "PLANNED");
  }

  @Test
  void aValveInShiftThatIsClosedIsOpened() {
    InsightsRecommendations answer =
        RulesInsights.recommend(snapshot("60", "CLOSED", List.of(shift(-60, 600)), List.of()), NOW);

    assertThat(answer.source()).isEqualTo("RULES");
    assertThat(answer.model()).isEqualTo("rules-v1");
    assertThat(answer.recommendations()).hasSize(1);
    assertThat(answer.recommendations().getFirst().action()).isEqualTo("OPEN");
    assertThat(answer.recommendations().getFirst().valveId()).isEqualTo(VALVE);
  }

  @Test
  void anOpenValveOutOfShiftIsClosed() {
    InsightsRecommendations answer =
        RulesInsights.recommend(
            snapshot("60", "OPEN", List.of(shift(3_600, 7_200)), List.of()), NOW);

    assertThat(answer.recommendations().getFirst().action()).isEqualTo("CLOSE");
  }

  @Test
  void atTheReserveFloorEveryOpenValveIsClosed() {
    InsightsRecommendations answer =
        RulesInsights.recommend(snapshot("20", "OPEN", List.of(shift(-60, 600)), List.of()), NOW);

    assertThat(answer.recommendations())
        .extracting(InsightRecommendation::action)
        .containsExactly("CLOSE");
    assertThat(answer.summary()).contains("reserva");
  }

  @Test
  void atTheReserveFloorWithNothingOpenItWaits() {
    InsightsRecommendations answer =
        RulesInsights.recommend(snapshot("10", "CLOSED", List.of(), List.of()), NOW);

    assertThat(answer.recommendations())
        .extracting(InsightRecommendation::action)
        .containsExactly("WAIT");
  }

  @Test
  void withoutALevelItAsksForAnInspection() {
    InsightsRecommendations answer =
        RulesInsights.recommend(snapshot(null, "OPEN", List.of(), List.of()), NOW);

    assertThat(answer.recommendations())
        .extracting(InsightRecommendation::action)
        .containsExactly("INSPECT");
  }

  @Test
  void aSeriousAnomalyAsksForAnInspectionAndNothingToDoWaits() {
    InsightsRecommendations serious =
        RulesInsights.recommend(
            snapshot(
                "60",
                "UNKNOWN",
                List.of(),
                List.of(new InsightsSnapshot.Anomaly("POSSIBLE_LEAK", "HIGH"))),
            NOW);
    InsightsRecommendations quiet =
        RulesInsights.recommend(snapshot("60", "UNKNOWN", List.of(), List.of()), NOW);

    assertThat(serious.recommendations())
        .extracting(InsightRecommendation::action)
        .containsExactly("INSPECT");
    assertThat(quiet.recommendations())
        .extracting(InsightRecommendation::action)
        .containsExactly("WAIT");
  }

  @Test
  void theAnswerToAQuestionDescribesTheTankAndTheForecast() {
    InsightsAnswer answer = RulesInsights.ask(snapshot("60", "OPEN", List.of(), List.of()), NOW);

    assertThat(answer.source()).isEqualTo("RULES");
    assertThat(answer.answer()).contains("60", "bajar", "25", "Pronóstico");
    InsightsAnswer none = RulesInsights.ask(snapshot(null, "OPEN", List.of(), List.of()), NOW);
    assertThat(none.answer()).contains("No hay una lectura");
  }

  @Test
  void theReportListsEveryGroupOfNumbers() {
    ReportRequest request =
        new ReportRequest(
            "Vereda de Prueba",
            NOW.minusSeconds(86_400),
            NOW,
            Map.of("levels", Map.of("readings", 4), "note", "texto"));

    InsightsReport report = RulesInsights.report(request, NOW);

    assertThat(report.source()).isEqualTo("RULES");
    assertThat(report.title()).contains("Vereda de Prueba", "2026-11-06", "2026-11-07");
    assertThat(report.findings()).hasSize(1);
    assertThat(report.findings().getFirst()).contains("levels", "readings = 4");
    assertThat(report.markdown()).startsWith("# Reporte").contains("## Hallazgos");
  }
}
