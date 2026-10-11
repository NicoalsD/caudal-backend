package co.caudal.application.rules;

import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.application.rules.RuleSetView.SectorSettingView;
import co.caudal.application.rules.RuleSetView.ValveOrderView;
import co.caudal.application.rules.RuleSetView.WindowView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Versions of the rules for the tests of the application layer. */
final class RuleSetFixtures {

  static final UUID SECTOR = UUID.randomUUID();
  static final UUID VALVE = UUID.randomUUID();

  private RuleSetFixtures() {}

  static RuleSetView view(UUID id, int version, List<BandView> bands) {
    return new RuleSetView(
        id,
        version,
        Instant.parse("2026-10-01T00:00:00Z"),
        new BigDecimal("1.00"),
        "REDUCE_TO_CRITICAL_BAND",
        new BigDecimal("16.00"),
        new BigDecimal("1.00"),
        new BigDecimal("4.00"),
        "PRIORITY_THEN_LONGEST_WAIT",
        30,
        new BigDecimal("0.50"),
        12,
        7,
        3,
        30,
        new BigDecimal("0.20"),
        bands,
        List.of(new SectorSettingView(SECTOR, true, true, 1)),
        List.of(new ValveOrderView(VALVE, 1)),
        List.of(new WindowView(null, LocalTime.of(5, 0), LocalTime.of(21, 0))));
  }

  static List<BandView> seedBands() {
    return List.of(
        new BandView(
            "CRITICAL",
            new BigDecimal("0.00"),
            new BigDecimal("1.50"),
            new BigDecimal("3.00"),
            true),
        new BandView(
            "LOW", new BigDecimal("1.50"), new BigDecimal("3.50"), new BigDecimal("8.00"), false),
        new BandView(
            "HIGH",
            new BigDecimal("3.50"),
            new BigDecimal("5.00"),
            new BigDecimal("16.00"),
            false));
  }

  static RuleSetDetail detail(UUID id, int version, String status, List<BandView> bands) {
    return new RuleSetDetail(
        view(id, version, bands),
        status,
        null,
        null,
        "DRAFT".equals(status) ? null : "Versión de prueba.",
        UUID.randomUUID(),
        Instant.parse("2026-10-01T00:00:00Z"),
        null,
        null);
  }
}
