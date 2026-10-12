package co.caudal.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.Parameters;
import co.caudal.domain.rules.RuleSet.SectorSetting;
import co.caudal.domain.rules.RuleSet.ValveOrder;
import co.caudal.domain.rules.RuleSetValidator.Gauge;
import co.caudal.domain.rules.RuleSetValidator.Violation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleSetValidatorTest {

  private static final UUID TANK = UUID.randomUUID();
  private static final List<Gauge> GAUGE =
      List.of(new Gauge(TANK, new BigDecimal("0.00"), new BigDecimal("5.00")));

  private static LevelBand band(String name, String min, String max) {
    return new LevelBand(
        name, new BigDecimal(min), new BigDecimal(max), new BigDecimal("8.00"), false);
  }

  private static RuleSet withBands(List<LevelBand> bands) {
    RuleSet seed = RuleSetTest.active();
    return new RuleSet(
        seed.id(), 4, "DRAFT", null, seed.parameters(), bands, List.of(), List.of(), List.of());
  }

  private static List<String> codes(List<Violation> violations) {
    return violations.stream().map(Violation::code).toList();
  }

  @Test
  void theSeedBandsCoverTheWholeGauge() {
    assertThat(RuleSetValidator.validate(RuleSetTest.active(), GAUGE, Map.of())).isEmpty();
  }

  @Test
  void aBandThatStopsShortLeavesAGapWithItsLimits() {
    List<Violation> violations =
        RuleSetValidator.validate(
            withBands(
                List.of(
                    band("CRITICAL", "0.00", "1.50"),
                    band("LOW", "1.50", "3.00"),
                    band("HIGH", "3.50", "5.00"))),
            GAUGE,
            Map.of());

    assertThat(codes(violations)).containsExactly("BAND_GAP");
    assertThat(violations.getFirst().details())
        .containsEntry("tank_id", TANK.toString())
        .containsEntry("gaps", List.of(Map.of("from", "3.00", "to", "3.50")));
  }

  @Test
  void findsGapsAtBothEndsAndOverlaps() {
    List<Violation> violations =
        RuleSetValidator.validate(
            withBands(List.of(band("LOW", "0.50", "3.60"), band("HIGH", "3.50", "4.80"))),
            GAUGE,
            Map.of());

    assertThat(codes(violations)).containsExactly("BAND_OVERLAP", "BAND_GAP");
    assertThat(violations.get(1).details().get("gaps"))
        .isEqualTo(
            List.of(Map.of("from", "0.00", "to", "0.50"), Map.of("from", "4.80", "to", "5.00")));
  }

  @Test
  void rejectsNoBandsEmptyBandsAndBadHours() {
    assertThat(codes(RuleSetValidator.validate(withBands(List.of()), GAUGE, Map.of())))
        .containsExactly("NO_BANDS");
    LevelBand inverted = band("LOW", "2.00", "2.00");
    LevelBand tooLong =
        new LevelBand(
            "HIGH", new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("25"), false);
    assertThat(
            codes(
                RuleSetValidator.validate(
                    withBands(List.of(band("CRITICAL", "0.00", "2.00"), inverted, tooLong)),
                    GAUGE,
                    Map.of())))
        .contains("BAND_EMPTY", "BAND_HOURS_OUT_OF_RANGE");

    Parameters seed = RuleSetTest.PARAMETERS;
    Parameters bad =
        new Parameters(
            seed.reserveLevel(),
            seed.reservePolicy(),
            new BigDecimal("25.00"),
            new BigDecimal("4.00"),
            new BigDecimal("2.00"),
            seed.allocationStrategy(),
            seed.duplicateWindowMinutes(),
            seed.maxLevelChangePerHour(),
            seed.staleReadingHours(),
            seed.maxBackdateDays(),
            seed.forecastHorizonDays(),
            seed.forecastContextDays(),
            seed.trendThresholdPerDay());
    RuleSet badHours =
        new RuleSet(
            null,
            4,
            "DRAFT",
            null,
            bad,
            RuleSetTest.active().bands(),
            List.of(),
            List.of(),
            List.of());
    assertThat(codes(RuleSetValidator.validate(badHours, GAUGE, Map.of())))
        .containsExactly("SHIFT_LENGTH_INVALID", "DAILY_HOURS_OUT_OF_RANGE");
  }

  @Test
  void everyValveOfAnIncludedSectorNeedsItsPlaceInTheOrder() {
    UUID included = UUID.randomUUID();
    UUID excluded = UUID.randomUUID();
    UUID ordered = UUID.randomUUID();
    UUID forgotten = UUID.randomUUID();
    RuleSet seed = RuleSetTest.active();
    RuleSet rules =
        new RuleSet(
            null,
            4,
            "DRAFT",
            null,
            seed.parameters(),
            seed.bands(),
            List.of(
                new SectorSetting(included, true, false, null),
                new SectorSetting(excluded, false, false, null)),
            List.of(new ValveOrder(ordered, 1)),
            List.of());

    List<Violation> violations =
        RuleSetValidator.validate(
            rules,
            GAUGE,
            Map.of(included, Set.of(ordered, forgotten), excluded, Set.of(UUID.randomUUID())));

    assertThat(codes(violations)).containsExactly("VALVE_ORDER_MISSING");
    assertThat(violations.getFirst().details())
        .containsEntry("valve_ids", List.of(forgotten.toString()));
  }
}
