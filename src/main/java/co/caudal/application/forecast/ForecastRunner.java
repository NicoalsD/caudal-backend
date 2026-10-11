package co.caudal.application.forecast;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.auth.Caller;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.ForecasterPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.rules.RuleSetView;
import co.caudal.domain.forecast.DailyLevel;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Forecasts a tank and stores the result (docs/Modulo-IA.md, sections 6 and 8): it reads the
 * effective levels of the last {@code forecast_context_days}, asks the {@link ForecasterPort}
 * outside any transaction, so a slow service never holds a database connection, and then stores the
 * primary run and, when the service answered, the simple estimate of the shadow.
 */
public class ForecastRunner {

  private final NetworkPort network;
  private final RuleSetPort ruleSets;
  private final ReadingPort readings;
  private final AqueductPort aqueducts;
  private final ForecasterPort forecaster;
  private final ForecastRunPort runs;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /** What was read before calling the forecaster. */
  private record Input(TankView tank, RuleSetView rules, List<LevelPoint> points, ZoneId zone) {}

  /**
   * Creates the runner.
   *
   * @param network tanks
   * @param ruleSets rules
   * @param readings effective levels
   * @param aqueducts time zone of the aqueduct
   * @param forecaster the service with its fallback and shadow
   * @param runs stored runs
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ForecastRunner(
      NetworkPort network,
      RuleSetPort ruleSets,
      ReadingPort readings,
      AqueductPort aqueducts,
      ForecasterPort forecaster,
      ForecastRunPort runs,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.network = network;
    this.ruleSets = ruleSets;
    this.readings = readings;
    this.aqueducts = aqueducts;
    this.forecaster = forecaster;
    this.runs = runs;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  /**
   * Forecasts a tank.
   *
   * @param caller who asks
   * @param tankId the tank
   * @param horizonDays days to forecast, or null for the {@code forecast_horizon_days} of the rules
   * @return the primary run
   * @throws DomainException with {@code NOT_FOUND} (tank or rules), {@code VALIDATION_ERROR}
   *     (horizon) or {@code NO_LEVEL_DATA} (no reading in the context window)
   */
  public StoredForecast run(Caller caller, UUID tankId, Integer horizonDays) {
    Instant now = clock.instant();
    Input input = unitOfWork.executeAs(caller.scope(), () -> read(caller, tankId, now));
    int horizon = horizonDays == null ? input.rules().forecastHorizonDays() : horizonDays;
    if (horizon < FieldLimits.FORECAST_HORIZON_DAYS_MIN
        || horizon > FieldLimits.FORECAST_HORIZON_DAYS_MAX) {
      throw new DomainException(ErrorCode.VALIDATION_ERROR);
    }
    ForecastRequest request =
        new ForecastRequest(
            new ForecastSeries(tankId, input.points(), horizon, input.zone()),
            daily(input.points(), input.zone()),
            input.tank().gaugeMin(),
            input.tank().gaugeMax());
    ForecastOutcome outcome = forecaster.forecast(request);
    Instant from = input.points().getFirst().observedAt();
    Instant to = input.points().getLast().observedAt();
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          UUID runId =
              runs.insert(
                  record(
                      caller,
                      input,
                      outcome.primary(),
                      outcome.fallback(),
                      outcome.fallbackReason(),
                      horizon,
                      from,
                      to));
          if (outcome.shadow() != null) {
            runs.insert(
                record(
                    caller,
                    input,
                    outcome.shadow(),
                    false,
                    ForecastOutcome.SHADOW_BASELINE,
                    horizon,
                    from,
                    to));
          }
          return new StoredForecast(
              runId,
              outcome.primary().modelName(),
              outcome.primary().modelVersion(),
              outcome.fallback(),
              outcome.fallbackReason(),
              horizon,
              clock.instant(),
              from,
              to,
              outcome.primary().points());
        });
  }

  private Input read(Caller caller, UUID tankId, Instant now) {
    TankView tank =
        network
            .tank(tankId)
            .filter(TankView::active)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSetView rules =
        ruleSets.findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    List<LevelPoint> points =
        readings.effectiveSince(tankId, now.minus(Duration.ofDays(rules.forecastContextDays())));
    if (points.isEmpty()) {
      throw new DomainException(ErrorCode.NO_LEVEL_DATA);
    }
    ZoneId zone =
        aqueducts
            .findById(caller.aqueductId())
            .map(AqueductRef::timezone)
            .map(ZoneId::of)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    return new Input(tank, rules, points, zone);
  }

  /** The last level of each local day, oldest first. */
  static List<DailyLevel> daily(List<LevelPoint> points, ZoneId zone) {
    Map<LocalDate, LevelPoint> lastOfDay = new TreeMap<>();
    for (LevelPoint point : points) {
      lastOfDay.merge(
          point.observedAt().atZone(zone).toLocalDate(),
          point,
          (a, b) -> b.observedAt().isAfter(a.observedAt()) ? b : a);
    }
    return lastOfDay.entrySet().stream()
        .map(e -> new DailyLevel(e.getKey(), e.getValue().value()))
        .toList();
  }

  private static ForecastRunRecord record(
      Caller caller,
      Input input,
      ModelForecast forecast,
      boolean fallback,
      String reason,
      int horizon,
      Instant from,
      Instant to) {
    return new ForecastRunRecord(
        caller.aqueductId(),
        input.tank().id(),
        forecast,
        fallback,
        reason,
        horizon,
        from,
        to,
        input.points().size(),
        input.rules().id(),
        caller.userId());
  }
}
