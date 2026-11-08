package co.caudal.domain.autopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.shared.error.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutopilotSettingsTest {

  private static final UUID AQUEDUCT = UUID.randomUUID();

  private static AutopilotSettings settings(int minutes, String floor) {
    return new AutopilotSettings(
        AQUEDUCT, AutopilotMode.ASSISTED, minutes, new BigDecimal(floor), Instant.EPOCH);
  }

  @Test
  void valuesInsideTheLimitsAreAccepted() {
    assertThat(settings(1, "0").operatorAbsenceMinutes()).isEqualTo(1);
    assertThat(settings(1_440, "100").reserveFloorPct()).isEqualByComparingTo("100");
  }

  @Test
  void valuesOutsideTheLimitsAreRefused() {
    assertThatThrownBy(() -> settings(0, "25")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> settings(1_441, "25")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> settings(30, "-0.01")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> settings(30, "100.01")).isInstanceOf(DomainException.class);
  }

  @Test
  void changingKeepsTheAqueductAndValidates() {
    AutopilotSettings changed =
        settings(30, "25").change(AutopilotMode.AUTOMATIC, 10, new BigDecimal("40"));

    assertThat(changed.aqueductId()).isEqualTo(AQUEDUCT);
    assertThat(changed.mode()).isSameAs(AutopilotMode.AUTOMATIC);
    assertThat(changed.operatorAbsenceMinutes()).isEqualTo(10);
    assertThatThrownBy(() -> changed.change(AutopilotMode.MANUAL, 0, BigDecimal.TEN))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void aRecommendationThatMovesAValveNeedsItsValve() {
    assertThatThrownBy(() -> new Recommendation(AutopilotAction.OPEN, null, "x", BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new Recommendation(AutopilotAction.WAIT, null, "x", BigDecimal.ONE).valveId())
        .isNull();
  }
}
