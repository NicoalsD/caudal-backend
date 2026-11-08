package co.caudal.application.autopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import co.caudal.application.auth.Caller;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.AutopilotDecisionPort;
import co.caudal.domain.event.OperatorActionRecorded;
import co.caudal.domain.event.ValveCommandConfirmed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutopilotSupportTest {

  private static final UUID AQUEDUCT = UUID.randomUUID();

  @Test
  void theTrackerKeepsTheLatestHumanActionPerAqueduct() {
    OperatorPresenceTracker tracker = new OperatorPresenceTracker();
    Instant early = Instant.parse("2026-11-07T10:00:00Z");
    Instant late = Instant.parse("2026-11-07T11:00:00Z");

    assertThat(tracker.lastActionAt(AQUEDUCT)).isEmpty();
    tracker.on(new OperatorActionRecorded(AQUEDUCT, UUID.randomUUID(), late));
    tracker.on(new OperatorActionRecorded(AQUEDUCT, UUID.randomUUID(), early));

    assertThat(tracker.lastActionAt(AQUEDUCT)).contains(late);
    assertThat(tracker.lastActionAt(UUID.randomUUID())).isEmpty();
    assertThat(tracker.eventType()).isEqualTo(OperatorActionRecorded.class);
  }

  @Test
  void aConfirmedCommandExecutesItsAcceptedDecisionOnlyWhenAcked() {
    AutopilotDecisionPort decisions = mock(AutopilotDecisionPort.class);
    DecisionExecutionObserver observer = new DecisionExecutionObserver(decisions);
    UUID command = UUID.randomUUID();

    observer.on(
        new ValveCommandConfirmed(AQUEDUCT, command, UUID.randomUUID(), "FAILED", Instant.EPOCH));
    verifyNoInteractions(decisions);
    observer.on(
        new ValveCommandConfirmed(AQUEDUCT, command, UUID.randomUUID(), "ACKED", Instant.EPOCH));

    verify(decisions).markExecutedByCommand(command);
    assertThat(observer.eventType()).isEqualTo(ValveCommandConfirmed.class);
  }

  @Test
  void theLevelIsAPercentOfTheGauge() {
    TankView tank =
        new TankView(
            UUID.randomUUID(),
            "T",
            new BigDecimal("1.00"),
            new BigDecimal("5.00"),
            new BigDecimal("0.05"),
            1000,
            true);

    assertThat(SnapshotBuilder.levelPct(tank, new BigDecimal("3.00"))).isEqualByComparingTo("50.0");
    assertThat(SnapshotBuilder.levelPct(tank, new BigDecimal("1.00"))).isEqualByComparingTo("0");
    assertThat(SnapshotBuilder.levelPct(tank, null)).isNull();
  }

  @Test
  void theRegionOfASectorIsItsRoot() {
    UUID region = UUID.randomUUID();
    UUID middle = UUID.randomUUID();
    UUID leaf = UUID.randomUUID();
    Map<UUID, SectorView> sectors = new HashMap<>();
    sectors.put(region, new SectorView(region, "N", "Norte", null, 0, true));
    sectors.put(middle, new SectorView(middle, "M", "Medio", region, 0, true));
    sectors.put(leaf, new SectorView(leaf, "L", "Hoja", middle, 0, true));

    assertThat(SnapshotBuilder.regionName(sectors.get(leaf), sectors)).isEqualTo("Norte");
    assertThat(SnapshotBuilder.regionName(sectors.get(middle), sectors)).isEqualTo("Norte");
    assertThat(SnapshotBuilder.regionName(sectors.get(region), sectors)).isNull();
    assertThat(SnapshotBuilder.regionName(null, sectors)).isNull();
  }

  @Test
  void theAutopilotIsTheOnlyCallerWithoutAnAccount() {
    Caller autopilot = Caller.autopilot(AQUEDUCT);

    assertThat(autopilot.userId()).isNull();
    assertThat(autopilot.scope().aqueductId()).isEqualTo(AQUEDUCT);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new Caller(null, AQUEDUCT, "OPERATOR", null))
        .isInstanceOf(NullPointerException.class);
  }
}
