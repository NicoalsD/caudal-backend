package co.caudal.application.autopilot;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.facade.TankStatusFacade;
import co.caudal.application.forecast.StoredForecast;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.AutopilotContextPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.rules.RuleSetView;
import co.caudal.application.rules.RuleSetView.ValveOrderView;
import co.caudal.domain.autopilot.AutopilotSettings;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.PhysicalConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Takes the snapshot of the aqueduct that the assistant reasons about: the tank with its level and
 * trend, the forecast, the valves with their state, the shifts around now and the open anomalies.
 * It reads only; the caller runs it inside a transaction scoped to the aqueduct.
 */
public class SnapshotBuilder {

  private static final Duration SHIFTS_BEFORE =
      Duration.ofHours(PhysicalConstants.HOURS_PER_DAY / 4);
  private static final Duration SHIFTS_AFTER = Duration.ofHours(PhysicalConstants.HOURS_PER_DAY);
  private static final Duration TREND_WINDOW = Duration.ofHours(PhysicalConstants.HOURS_PER_DAY);
  private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
  private static final int PCT_SCALE = 1;
  private static final int RATIO_SCALE = 6;

  private final NetworkPort network;
  private final RuleSetPort ruleSets;
  private final ReadingPort readings;
  private final ForecastRunPort forecasts;
  private final AutopilotContextPort context;
  private final AqueductPort aqueducts;
  private final Clock clock;

  /**
   * Creates the builder.
   *
   * @param network tanks and sectors
   * @param ruleSets the active rules
   * @param readings effective levels
   * @param forecasts stored forecasts
   * @param context valves, shifts and anomalies
   * @param aqueducts the aqueduct and its time zone
   * @param clock source of time
   */
  // The builder gathers the reads of one snapshot; each collaborator is one source.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public SnapshotBuilder(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      ForecastRunPort forecasts,
      AutopilotContextPort context,
      AqueductPort aqueducts,
      Clock clock) {
    this.network = network;
    this.ruleSets = ruleSets;
    this.readings = readings;
    this.forecasts = forecasts;
    this.context = context;
    this.aqueducts = aqueducts;
    this.clock = clock;
  }

  /**
   * The tank the autopilot watches: the first active one by name.
   *
   * @return the tank, or empty if the aqueduct has none
   */
  public Optional<TankView> watchedTank() {
    return network.tanks().stream().filter(TankView::active).findFirst();
  }

  /**
   * The level of a tank in percent of its gauge.
   *
   * @param tank the tank
   * @param level the level in meters of gauge, or null
   * @return the percent, or null if the level is unknown
   */
  public static BigDecimal levelPct(TankView tank, BigDecimal level) {
    if (level == null) {
      return null;
    }
    BigDecimal range = tank.gaugeMax().subtract(tank.gaugeMin());
    return level
        .subtract(tank.gaugeMin())
        .divide(range, RATIO_SCALE, RoundingMode.HALF_UP)
        .multiply(PERCENT)
        .setScale(PCT_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * The current level of the watched tank in percent, or null when unknown.
   *
   * @return the percent
   */
  public BigDecimal currentLevelPct() {
    return watchedTank()
        .map(
            tank ->
                levelPct(
                    tank, readings.latestEffective(tank.id()).map(LevelPoint::value).orElse(null)))
        .orElse(null);
  }

  /**
   * Takes the snapshot.
   *
   * @param aqueductId the aqueduct of the session
   * @param settings its autopilot settings
   * @param lastHumanAction the last human action on the valves, or null
   * @return the snapshot, or empty if the aqueduct has no active tank
   */
  public Optional<InsightsSnapshot> build(
      UUID aqueductId, AutopilotSettings settings, Instant lastHumanAction) {
    Optional<TankView> watched = watchedTank();
    if (watched.isEmpty()) {
      return Optional.empty();
    }
    AqueductRef aqueduct = aqueducts.findById(aqueductId).orElse(null);
    ZoneId zone = aqueduct == null ? ZoneId.of("UTC") : ZoneId.of(aqueduct.timezone());
    Instant now = clock.instant();
    Optional<RuleSetView> rules = ruleSets.findActive();
    return Optional.of(
        new InsightsSnapshot(
            aqueductId,
            aqueduct == null ? "" : aqueduct.name(),
            zone,
            now,
            settings.mode().name(),
            settings.reserveFloorPct(),
            lastHumanAction,
            tank(watched.get(), rules.orElse(null), now),
            forecast(watched.get().id(), zone),
            valves(rules.orElse(null)),
            cut(
                context.shifts(now.minus(SHIFTS_BEFORE), now.plus(SHIFTS_AFTER)),
                FieldLimits.INSIGHTS_SCHEDULE_MAX),
            cut(context.openAnomalies(), FieldLimits.INSIGHTS_ANOMALIES_MAX)));
  }

  private InsightsSnapshot.Tank tank(TankView tank, RuleSetView rules, Instant now) {
    LevelPoint last = readings.latestEffective(tank.id()).orElse(null);
    if (last == null) {
      return new InsightsSnapshot.Tank(
          tank.id(), tank.name(), null, tank.gaugeMax(), null, null, null, null);
    }
    return new InsightsSnapshot.Tank(
        tank.id(),
        tank.name(),
        last.value(),
        tank.gaugeMax(),
        levelPct(tank, last.value()),
        rules == null ? null : TankStatusFacade.band(rules, tank, last.value()),
        last.observedAt(),
        trend(tank, rules, now));
  }

  /**
   * RISING, FALLING or STABLE from the slope of the last day against the threshold of the rules.
   */
  private String trend(TankView tank, RuleSetView rules, Instant now) {
    List<LevelPoint> recent = readings.effectiveSince(tank.id(), now.minus(TREND_WINDOW));
    if (recent.size() < 2 || rules == null) {
      return null;
    }
    LevelPoint first = recent.getFirst();
    LevelPoint last = recent.getLast();
    long seconds = Duration.between(first.observedAt(), last.observedAt()).toSeconds();
    if (seconds <= 0) {
      return null;
    }
    double perDay =
        last.value().subtract(first.value()).doubleValue()
            * Duration.ofDays(1).toSeconds()
            / seconds;
    double threshold = rules.trendThresholdPerDay().doubleValue();
    if (perDay > threshold) {
      return "RISING";
    }
    return perDay < -threshold ? "FALLING" : "STABLE";
  }

  private List<InsightsSnapshot.ForecastPoint> forecast(UUID tankId, ZoneId zone) {
    StoredForecast stored = forecasts.latestPrimary(tankId).orElse(null);
    if (stored == null) {
      return List.of();
    }
    List<InsightsSnapshot.ForecastPoint> points = new ArrayList<>();
    stored.points().stream()
        .limit(FieldLimits.INSIGHTS_FORECAST_MAX)
        .forEach(
            point ->
                points.add(
                    new InsightsSnapshot.ForecastPoint(
                        point.targetDate().atStartOfDay(zone).toInstant(),
                        point.p10(),
                        point.p50(),
                        point.p90())));
    return points;
  }

  private List<InsightsSnapshot.Valve> valves(RuleSetView rules) {
    Map<UUID, SectorView> sectors =
        network.sectors().stream().collect(Collectors.toMap(SectorView::id, s -> s));
    Map<UUID, Integer> order = new HashMap<>();
    if (rules != null) {
      for (ValveOrderView entry : rules.valveOrder()) {
        order.put(entry.valveId(), entry.sequenceOrder());
      }
    }
    List<InsightsSnapshot.Valve> result = new ArrayList<>();
    int position = 0;
    for (ValveRow row : context.valves()) {
      position++;
      if (!row.active()) {
        continue;
      }
      SectorView sector = sectors.get(row.live().sectorId());
      result.add(
          new InsightsSnapshot.Valve(
              row.live().valveId(),
              row.live().sectorId(),
              sector == null ? "" : sector.name(),
              regionName(sector, sectors),
              row.live().state(),
              row.motorized(),
              order.getOrDefault(row.live().valveId(), position)));
    }
    return cut(result, FieldLimits.INSIGHTS_VALVES_MAX);
  }

  /** The name of the root sector above a sector, or null when the sector is itself a root. */
  static String regionName(SectorView sector, Map<UUID, SectorView> sectors) {
    if (sector == null) {
      return null;
    }
    Map<UUID, Boolean> seen = new LinkedHashMap<>();
    SectorView current = sector;
    while (current.parentSectorId() != null && seen.put(current.id(), true) == null) {
      SectorView parent = sectors.get(current.parentSectorId());
      if (parent == null) {
        break;
      }
      current = parent;
    }
    return current.id().equals(sector.id()) ? null : current.name();
  }

  private static <T> List<T> cut(List<T> list, int max) {
    return list.size() <= max ? list : list.subList(0, max);
  }
}
