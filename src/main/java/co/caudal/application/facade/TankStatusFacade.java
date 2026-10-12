package co.caudal.application.facade;

import co.caudal.application.auth.Caller;
import co.caudal.application.forecast.ForecastRunner;
import co.caudal.application.forecast.TrendView;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.AnomalyPort;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.rules.RuleSetView;
import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.domain.forecast.DailyLevel;
import co.caudal.domain.tank.TankLevelState;
import co.caudal.domain.tank.TrendStrategy;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One call for the whole status of a tank (docs/Reglas-de-negocio.md, section 10): the last level
 * and its age ({@link TankLevelState}), the trend ({@link TrendStrategy}), the band of the rules,
 * the latest forecast and the open anomalies. A stale level opens a {@code STALE_DATA} anomaly
 * once. The panel and the app ask this facade instead of six use cases.
 *
 * @pattern P10 Facade
 */
public class TankStatusFacade {

  private static final String STALE_DATA = "STALE_DATA";
  private static final int HOURS_SCALE = 1;
  private static final BigDecimal SECONDS_PER_HOUR =
      BigDecimal.valueOf(Duration.ofHours(1).toSeconds());

  private final NetworkPort network;
  private final RuleSetPort ruleSets;
  private final ReadingPort readings;
  private final AqueductPort aqueducts;
  private final ForecastRunPort forecasts;
  private final AnomalyPort anomalies;
  private final TrendStrategy trend;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the facade.
   *
   * @param network tanks
   * @param ruleSets rules
   * @param readings effective levels
   * @param aqueducts time zone
   * @param forecasts stored forecasts
   * @param anomalies anomalies
   * @param trend how the trend is computed
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  // The facade exists to hide these collaborators from its callers.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public TankStatusFacade(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      AqueductPort aqueducts,
      ForecastRunPort forecasts,
      AnomalyPort anomalies,
      TrendStrategy trend,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.network = network;
    this.ruleSets = ruleSets;
    this.readings = readings;
    this.aqueducts = aqueducts;
    this.forecasts = forecasts;
    this.anomalies = anomalies;
    this.trend = trend;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * The status of a tank.
   *
   * @param caller who asks
   * @param tankId the tank
   * @return the status
   * @throws DomainException with {@code NOT_FOUND} for an unknown tank or missing rules
   */
  public TankStatus status(Caller caller, UUID tankId) {
    return unitOfWork.executeAs(caller.scope(), () -> build(caller, tankId));
  }

  private TankStatus build(Caller caller, UUID tankId) {
    TankView tank =
        network.tank(tankId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSetView rules =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Instant now = clock.instant();
    LevelPoint last = readings.latestEffective(tankId).orElse(null);
    TankLevelState state =
        TankLevelState.of(
            last == null ? null : last.value(),
            last == null ? null : last.observedAt(),
            now,
            rules.staleReadingHours());
    if (state.opensStaleAnomaly() && !anomalies.openExists(STALE_DATA, tankId)) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("tank_id", tankId.toString());
      evidence.put("last_observed_at", last.observedAt().toString());
      evidence.put("stale_after_hours", rules.staleReadingHours());
      anomalies.open(caller.aqueductId(), STALE_DATA, evidence);
    }
    return new TankStatus(
        tank.id(),
        last == null ? null : last.value(),
        last == null ? null : last.observedAt(),
        last == null ? null : hoursAgo(last.observedAt(), now),
        last == null ? null : trend(caller, tankId, rules, last.observedAt()),
        state.stale(),
        rules.staleReadingHours(),
        last == null ? null : band(rules, tank, last.value()),
        forecasts.latestPrimary(tankId).orElse(null),
        anomalies.countOpen());
  }

  private TrendView trend(Caller caller, UUID tankId, RuleSetView rules, Instant lastAt) {
    ZoneId zone =
        aqueducts
            .findById(caller.aqueductId())
            .map(a -> ZoneId.of(a.timezone()))
            .orElse(ZoneId.of("UTC"));
    List<LevelPoint> recent =
        readings.effectiveSince(
            tankId, lastAt.minus(Duration.ofDays(FieldLimits.TREND_WINDOW_DAYS)));
    List<DailyLevel> window = ForecastRunner.daily(recent, zone);
    return trend
        .trend(window, rules.trendThresholdPerDay())
        .map(t -> new TrendView(t.slopePerDay(), t.label(), t.labelEs()))
        .orElse(null);
  }

  private static BigDecimal hoursAgo(Instant observedAt, Instant now) {
    return BigDecimal.valueOf(Duration.between(observedAt, now).toSeconds())
        .divide(SECONDS_PER_HOUR, HOURS_SCALE, RoundingMode.HALF_UP);
  }

  /** Bands are {@code [min, max)}; the band that ends at the top of the gauge includes it. */
  static String band(RuleSetView rules, TankView tank, BigDecimal level) {
    for (BandView band : rules.bands()) {
      boolean top = band.maxLevel().compareTo(tank.gaugeMax()) >= 0;
      boolean above = level.compareTo(band.minLevel()) >= 0;
      boolean below =
          top ? level.compareTo(band.maxLevel()) <= 0 : level.compareTo(band.maxLevel()) < 0;
      if (above && below) {
        return band.band();
      }
    }
    return null;
  }
}
