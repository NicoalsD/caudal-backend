package co.caudal.domain.autopilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModeStrategyTest {

  private static final Instant NOW = Instant.parse("2026-11-07T15:00:00Z");
  private static final UUID VALVE = UUID.randomUUID();

  private static Recommendation open() {
    return new Recommendation(AutopilotAction.OPEN, VALVE, "Abrir.", BigDecimal.ONE);
  }

  private static Recommendation close() {
    return new Recommendation(AutopilotAction.CLOSE, VALVE, "Cerrar.", BigDecimal.ONE);
  }

  private static Recommendation waiting() {
    return new Recommendation(AutopilotAction.WAIT, null, "Esperar.", BigDecimal.ONE);
  }

  private static CycleContext context(String level, Instant lastHuman) {
    return new CycleContext(
        level == null ? null : new BigDecimal(level), new BigDecimal("25"), lastHuman, NOW, 30);
  }

  @Test
  void eachModeHasItsOwnStrategy() {
    for (AutopilotMode mode : AutopilotMode.values()) {
      assertThat(ModeStrategy.of(mode).mode()).isSameAs(mode);
    }
  }

  @Test
  void manualAndAssistedLeaveTheDecisionPending() {
    for (AutopilotMode mode : new AutopilotMode[] {AutopilotMode.MANUAL, AutopilotMode.ASSISTED}) {
      Resolution resolution = ModeStrategy.of(mode).resolve(close(), context("80", null));

      assertThat(resolution.outcome()).isSameAs(Resolution.Outcome.PENDING);
      assertThat(resolution.detail()).isNull();
    }
  }

  @Test
  void anOpenAtOrUnderTheReserveFloorIsSkippedInEveryMode() {
    for (AutopilotMode mode : AutopilotMode.values()) {
      for (String level : new String[] {"25", "24.9", "0", null}) {
        Resolution resolution = ModeStrategy.of(mode).resolve(open(), context(level, null));

        assertThat(resolution.outcome())
            .as("%s at %s", mode, level)
            .isSameAs(Resolution.Outcome.SKIPPED);
        assertThat(resolution.detail()).isEqualTo(ReserveFloorGuard.CODE);
      }
    }
  }

  @Test
  void aCloseIsNeverBlockedByTheReserveFloor() {
    Resolution resolution =
        ModeStrategy.of(AutopilotMode.AUTOMATIC).resolve(close(), context("5", null));

    assertThat(resolution.outcome()).isSameAs(Resolution.Outcome.EXECUTE);
  }

  @Test
  void automaticExecutesWhenNobodyActedForTheAbsenceTime() {
    ModeStrategy automatic = ModeStrategy.of(AutopilotMode.AUTOMATIC);

    assertThat(automatic.resolve(open(), context("80", null)).outcome())
        .isSameAs(Resolution.Outcome.EXECUTE);
    assertThat(automatic.resolve(close(), context("80", NOW.minusSeconds(30 * 60))).outcome())
        .as("exactly at the absence time the operator is gone")
        .isSameAs(Resolution.Outcome.EXECUTE);
  }

  @Test
  void aRecentHumanActionPausesTheAutomaticMode() {
    Resolution resolution =
        ModeStrategy.of(AutopilotMode.AUTOMATIC)
            .resolve(close(), context("80", NOW.minusSeconds(60)));

    assertThat(resolution.outcome()).isSameAs(Resolution.Outcome.PENDING);
    assertThat(resolution.detail()).isEqualTo(HumanPriorityGuard.CODE);
  }

  @Test
  void aWaitIsNeverExecuted() {
    Resolution resolution =
        ModeStrategy.of(AutopilotMode.AUTOMATIC).resolve(waiting(), context("80", null));

    assertThat(resolution.outcome()).isSameAs(Resolution.Outcome.PENDING);
    assertThat(resolution.detail()).isNull();
  }

  @Test
  void theGuardsAnswerOnTheirOwn() {
    assertThat(new ReserveFloorGuard().allows(AutopilotAction.OPEN, context("26", null))).isTrue();
    assertThat(new ReserveFloorGuard().allows(AutopilotAction.INSPECT, context(null, null)))
        .isTrue();
    assertThat(new HumanPriorityGuard().allows(AutopilotAction.CLOSE, context("80", null)))
        .isTrue();
    assertThat(Resolution.skipped("X").detail()).isEqualTo("X");
    assertThat(Resolution.execute().outcome()).isSameAs(Resolution.Outcome.EXECUTE);
  }
}
