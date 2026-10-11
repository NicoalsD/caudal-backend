package co.caudal.domain.rules;

import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.shared.PhysicalConstants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Checks a version of the rules before it is activated (docs/Reglas-de-negocio.md, section 2):
 * bands without overlaps that cover the whole gauge of every tank, consistent shift lengths and
 * daily hours, and an opening order for every valve of every included sector.
 */
public final class RuleSetValidator {

  private static final BigDecimal HOURS_PER_DAY =
      BigDecimal.valueOf(PhysicalConstants.HOURS_PER_DAY);

  /**
   * The painted gauge of a tank.
   *
   * @param tankId the tank
   * @param min lowest value
   * @param max highest value
   */
  public record Gauge(UUID tankId, BigDecimal min, BigDecimal max) {}

  /**
   * One problem found.
   *
   * @param code what is wrong, for example {@code BAND_GAP}
   * @param details the values that explain it
   */
  public record Violation(String code, Map<String, Object> details) {

    /** Freezes the details. */
    public Violation {
      details = Map.copyOf(details);
    }
  }

  private RuleSetValidator() {}

  /**
   * Validates a version.
   *
   * @param rules the version
   * @param gauges the gauges of the active tanks of the aqueduct
   * @param valvesBySector the active valves of each sector
   * @return the problems, empty when the version can be activated
   */
  public static List<Violation> validate(
      RuleSet rules, List<Gauge> gauges, Map<UUID, Set<UUID>> valvesBySector) {
    List<Violation> violations = new ArrayList<>();
    List<LevelBand> bands =
        rules.bands().stream().sorted(Comparator.comparing(LevelBand::minLevel)).toList();
    checkBands(bands, violations);
    for (Gauge gauge : gauges) {
      checkCoverage(bands, gauge, violations);
    }
    checkHours(rules.parameters(), violations);
    checkValveOrder(rules, valvesBySector, violations);
    return List.copyOf(violations);
  }

  private static void checkBands(List<LevelBand> bands, List<Violation> violations) {
    if (bands.isEmpty()) {
      violations.add(new Violation("NO_BANDS", Map.of()));
      return;
    }
    for (int i = 0; i < bands.size(); i++) {
      LevelBand band = bands.get(i);
      if (band.maxLevel().compareTo(band.minLevel()) <= 0) {
        violations.add(new Violation("BAND_EMPTY", Map.of("band", band.band())));
      }
      if (band.dailyServiceHours().signum() < 0
          || band.dailyServiceHours().compareTo(HOURS_PER_DAY) > 0) {
        violations.add(new Violation("BAND_HOURS_OUT_OF_RANGE", Map.of("band", band.band())));
      }
      if (i > 0 && bands.get(i - 1).maxLevel().compareTo(band.minLevel()) > 0) {
        violations.add(
            new Violation(
                "BAND_OVERLAP", Map.of("bands", List.of(bands.get(i - 1).band(), band.band()))));
      }
    }
  }

  private static void checkCoverage(
      List<LevelBand> bands, Gauge gauge, List<Violation> violations) {
    if (bands.isEmpty()) {
      return;
    }
    List<Map<String, String>> gaps = new ArrayList<>();
    BigDecimal reached = gauge.min();
    for (LevelBand band : bands) {
      if (band.minLevel().compareTo(reached) > 0) {
        gaps.add(gap(reached, band.minLevel().min(gauge.max())));
      }
      if (band.maxLevel().compareTo(reached) > 0) {
        reached = band.maxLevel();
      }
      if (reached.compareTo(gauge.max()) >= 0) {
        break;
      }
    }
    if (reached.compareTo(gauge.max()) < 0) {
      gaps.add(gap(reached, gauge.max()));
    }
    gaps.removeIf(g -> g.get("from").equals(g.get("to")));
    if (!gaps.isEmpty()) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("tank_id", gauge.tankId().toString());
      details.put("gaps", gaps);
      violations.add(new Violation("BAND_GAP", details));
    }
  }

  private static Map<String, String> gap(BigDecimal from, BigDecimal to) {
    return Map.of("from", from.toPlainString(), "to", to.toPlainString());
  }

  private static void checkHours(RuleSet.Parameters p, List<Violation> violations) {
    if (p.maxShiftHours().compareTo(p.minShiftHours()) < 0) {
      violations.add(new Violation("SHIFT_LENGTH_INVALID", Map.of()));
    }
    if (p.maxDailyServiceHours().signum() <= 0
        || p.maxDailyServiceHours().compareTo(HOURS_PER_DAY) > 0) {
      violations.add(new Violation("DAILY_HOURS_OUT_OF_RANGE", Map.of()));
    }
  }

  private static void checkValveOrder(
      RuleSet rules, Map<UUID, Set<UUID>> valvesBySector, List<Violation> violations) {
    Set<UUID> ordered =
        rules.valveOrder().stream().map(RuleSet.ValveOrder::valveId).collect(Collectors.toSet());
    List<String> missing = new ArrayList<>();
    for (RuleSet.SectorSetting sector : rules.sectors()) {
      if (!sector.included()) {
        continue;
      }
      for (UUID valve : valvesBySector.getOrDefault(sector.sectorId(), Set.of())) {
        if (!ordered.contains(valve)) {
          missing.add(valve.toString());
        }
      }
    }
    if (!missing.isEmpty()) {
      missing.sort(Comparator.naturalOrder());
      violations.add(new Violation("VALVE_ORDER_MISSING", Map.of("valve_ids", missing)));
    }
  }
}
