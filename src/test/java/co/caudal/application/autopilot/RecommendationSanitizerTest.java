package co.caudal.application.autopilot;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.autopilot.AutopilotAction;
import co.caudal.domain.autopilot.Recommendation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationSanitizerTest {

  private static final UUID MOTORIZED = UUID.randomUUID();
  private static final UUID FIXED = UUID.randomUUID();
  private static final InsightsSnapshot SNAPSHOT =
      new InsightsSnapshot(
          UUID.randomUUID(),
          "Vereda",
          ZoneId.of("UTC"),
          Instant.EPOCH,
          "ASSISTED",
          new BigDecimal("25"),
          null,
          new InsightsSnapshot.Tank(
              UUID.randomUUID(), "Tanque", null, new BigDecimal("5"), null, null, null, null),
          List.of(),
          List.of(
              new InsightsSnapshot.Valve(
                  MOTORIZED, UUID.randomUUID(), "Alto", null, "OPEN", true, 1),
              new InsightsSnapshot.Valve(FIXED, UUID.randomUUID(), "Bajo", null, "OPEN", false, 2)),
          List.of(),
          List.of());

  private static List<Recommendation> clean(InsightRecommendation... items) {
    return RecommendationSanitizer.clean(
        new InsightsRecommendations("LLM", "m", Instant.EPOCH, "", List.of(items)), SNAPSHOT);
  }

  private static InsightRecommendation item(
      String action, UUID valve, String reason, String confidence) {
    return new InsightRecommendation(
        action, valve, reason, confidence == null ? null : new BigDecimal(confidence));
  }

  @Test
  void aValidRecommendationPassesWithItsConfidenceBetweenZeroAndOne() {
    List<Recommendation> result =
        clean(item("CLOSE", MOTORIZED, " Cerrar. ", "1.7"), item("WAIT", null, "Esperar", "-3"));

    assertThat(result)
        .extracting(Recommendation::action)
        .containsExactly(AutopilotAction.CLOSE, AutopilotAction.WAIT);
    assertThat(result.getFirst().reason()).isEqualTo("Cerrar.");
    assertThat(result.getFirst().confidence()).isEqualByComparingTo("1");
    assertThat(result.get(1).confidence()).isEqualByComparingTo("0");
  }

  @Test
  void invalidRecommendationsAreDropped() {
    List<Recommendation> result =
        clean(
            item("EXPLODE", MOTORIZED, "x", "0.5"),
            item("OPEN", UUID.randomUUID(), "valve not in the snapshot", "0.5"),
            item("OPEN", null, "no valve", "0.5"),
            item("OPEN", FIXED, "not motorized", "0.5"),
            item("WAIT", UUID.randomUUID(), "unknown valve on a wait", "0.5"),
            item("WAIT", null, " ", "0.5"),
            item("WAIT", null, "no confidence", null),
            item("WAIT", null, null, "0.5"));

    assertThat(result).isEmpty();
  }

  @Test
  void aLongReasonIsCutAndNoMoreThanFiveGoOn() {
    List<InsightRecommendation> many = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      many.add(item("WAIT", null, "r".repeat(400), "0.5"));
    }

    List<Recommendation> result = clean(many.toArray(InsightRecommendation[]::new));

    assertThat(result).hasSize(5);
    assertThat(result.getFirst().reason()).hasSize(280).endsWith("...");
  }
}
