package co.caudal.application.schedule;

import co.caudal.application.aqueduct.AqueductRef;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.facade.TankStatusFacade;
import co.caudal.application.forecast.ForecastPoint;
import co.caudal.application.forecast.StoredForecast;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.port.out.AqueductPort;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.ScheduleHistoryPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.reading.LevelPoint;
import co.caudal.application.rules.RuleSetView;
import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.application.rules.RuleSetView.SectorSettingView;
import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.domain.schedule.AllocationInput;
import co.caudal.domain.schedule.DailyHours;
import co.caudal.domain.schedule.ScheduleProposalBuilder;
import co.caudal.domain.schedule.SectorCandidate;
import co.caudal.domain.schedule.TurnAllocationStrategy;
import co.caudal.domain.schedule.TurnExplainer;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the proposal of shifts of a day (docs/Reglas-de-negocio.md, section 5): it reads the last
 * effective level and its band, the forecast of the day and the hours the rules allow, ranks the
 * sectors by priority and hours without service, hands out the hours with the strategy of the rules
 * and explains every shift. The proposal is stored pending the review of the Board.
 *
 * @pattern P13 Command
 */
public class GenerateProposalCommandHandler
    implements CommandHandler<GenerateProposalCommand, ProposalDetail> {

  /** A sector the rules leave out of the shifts. */
  static final String NOT_INCLUDED = "NOT_INCLUDED";

  /** A sector that is not a priority on a day only priority sectors get water. */
  static final String PRIORITY_ONLY = "PRIORITY_ONLY";

  private static final int SCALE = 2;
  private static final BigDecimal SECONDS_PER_HOUR =
      BigDecimal.valueOf(Duration.ofHours(1).toSeconds());

  private final ScheduleData data;
  private final ProposalPort proposals;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * The ports the handler reads the facts of the day from.
   *
   * @param ruleSets rules
   * @param network tanks and sectors
   * @param readings effective levels
   * @param forecasts stored forecasts
   * @param history past service of each sector
   * @param aqueducts time zone
   */
  public record ScheduleData(
      RuleSetPort ruleSets,
      NetworkPort network,
      ReadingPort readings,
      ForecastRunPort forecasts,
      ScheduleHistoryPort history,
      AqueductPort aqueducts) {}

  private record Facts(
      RuleSetView rules,
      TankView tank,
      LevelPoint level,
      String band,
      BandView bandView,
      Optional<StoredForecast> forecast,
      Optional<ForecastPoint> point,
      ZoneId zone) {}

  /**
   * Creates the handler.
   *
   * @param data the ports with the facts of the day
   * @param proposals proposals
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GenerateProposalCommandHandler(
      ScheduleData data, ProposalPort proposals, UnitOfWorkPort unitOfWork, Clock clock) {
    this.data = data;
    this.proposals = proposals;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public ProposalDetail handle(GenerateProposalCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          LocalDate day = command.serviceDate();
          if (proposals.openExists(day)) {
            throw new DomainException(ErrorCode.CONFLICT);
          }
          Facts facts = facts(command.caller().aqueductId(), day);
          UUID id =
              proposals.insert(
                  command.caller().aqueductId(), build(facts, day), command.caller().userId());
          return proposals
              .find(id, LocalDate.ofInstant(clock.instant(), facts.zone()))
              .orElseThrow();
        });
  }

  private Facts facts(UUID aqueductId, LocalDate day) {
    RuleSetView rules =
        data.ruleSets().findActive().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    TankView tank =
        data.network().tanks().stream()
            .filter(TankView::active)
            .findFirst()
            .orElseThrow(() -> new DomainException(ErrorCode.NO_LEVEL_DATA));
    LevelPoint level =
        data.readings()
            .latestEffective(tank.id())
            .orElseThrow(() -> new DomainException(ErrorCode.NO_LEVEL_DATA));
    String band = TankStatusFacade.band(rules, tank, level.value());
    BandView bandView =
        rules.bands().stream()
            .filter(b -> b.band().equals(band))
            .findFirst()
            .orElseThrow(() -> new DomainException(ErrorCode.NO_LEVEL_DATA));
    Optional<StoredForecast> forecast = data.forecasts().latestPrimary(tank.id());
    Optional<ForecastPoint> point =
        forecast.flatMap(
            f -> f.points().stream().filter(p -> p.targetDate().equals(day)).findFirst());
    ZoneId zone =
        data.aqueducts()
            .findById(aqueductId)
            .map(AqueductRef::timezone)
            .map(ZoneId::of)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    return new Facts(rules, tank, level, band, bandView, forecast, point, zone);
  }

  private co.caudal.domain.schedule.ScheduleProposal build(Facts facts, LocalDate day) {
    RuleSetView rules = facts.rules();
    BigDecimal p10 = facts.point().map(ForecastPoint::p10).orElse(null);
    DailyHours hours =
        DailyHours.compute(
                level(facts.bandView()),
                rules.bands().stream().map(GenerateProposalCommandHandler::level).toList(),
                rules.operatingWindows().stream()
                    .map(w -> new OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
                    .toList(),
                day,
                facts.zone(),
                rules.maxDailyServiceHours(),
                new DailyHours.ReserveGuard(p10, rules.reserveLevel(), rules.reservePolicy()))
            .orElseThrow(() -> new DomainException(ErrorCode.OUTSIDE_OPERATING_WINDOW));
    TurnAllocationStrategy strategy = TurnAllocationStrategy.forCode(rules.allocationStrategy());
    ScheduleProposalBuilder builder =
        new ScheduleProposalBuilder()
            .forDay(day, rules.id())
            .level(facts.level().value(), facts.band())
            .availableHours(hours.hours())
            .forecast(facts.forecast().map(StoredForecast::runId).orElse(null))
            .strategy(strategy.code());
    List<SectorCandidate> candidates = candidates(rules, hours.windowStart());
    for (SectorCandidate candidate : candidates) {
      if (!candidate.included()) {
        builder.unserved(candidate.sectorId(), NOT_INCLUDED);
      } else if (hours.priorityOnly() && !candidate.priority()) {
        builder.unserved(candidate.sectorId(), PRIORITY_ONLY);
      }
    }
    TurnExplainer explainer = new TurnExplainer(dayFacts(facts, day, hours));
    Function<SectorCandidate, List<co.caudal.domain.schedule.TurnReason>> reasons =
        explainer::explain;
    strategy.allocate(
        new AllocationInput(
            candidates,
            hours.priorityOnly(),
            hours.hours(),
            hours.windowStart(),
            rules.minShiftHours(),
            rules.maxShiftHours(),
            reasons),
        builder);
    return builder.build();
  }

  private static TurnExplainer.DayFacts dayFacts(Facts facts, LocalDate day, DailyHours hours) {
    Optional<ForecastPoint> point = facts.point();
    return new TurnExplainer.DayFacts(
        facts.level().value(),
        facts.band(),
        facts.bandView().dailyServiceHours(),
        point.map(ForecastPoint::p10).orElse(null),
        point.map(ForecastPoint::p50).orElse(null),
        point.map(ForecastPoint::p90).orElse(null),
        day,
        facts.rules().reserveLevel(),
        hours.reserveCut());
  }

  private List<SectorCandidate> candidates(RuleSetView rules, Instant reference) {
    Map<UUID, SectorSettingView> settings =
        rules.sectors().stream()
            .collect(Collectors.toMap(SectorSettingView::sectorId, Function.identity()));
    List<SectorCandidate> candidates = new ArrayList<>();
    for (SectorView sector : data.network().sectors()) {
      if (!sector.active()) {
        continue;
      }
      SectorSettingView setting = settings.get(sector.id());
      Optional<ServiceEnd> last = data.history().lastServiceEnd(sector.id(), reference);
      candidates.add(
          new SectorCandidate(
              sector.id(),
              sector.code(),
              sector.name(),
              setting == null || setting.included(),
              setting != null && setting.priority(),
              setting == null ? null : setting.priorityRank(),
              last.map(end -> hoursBetween(end.endedAt(), reference)).orElse(null),
              last.map(ServiceEnd::origin).orElse(null)));
    }
    return candidates;
  }

  static BigDecimal hoursBetween(Instant from, Instant to) {
    return BigDecimal.valueOf(Duration.between(from, to).toSeconds())
        .divide(SECONDS_PER_HOUR, SCALE, RoundingMode.DOWN);
  }

  private static LevelBand level(BandView band) {
    return new LevelBand(
        band.band(),
        band.minLevel(),
        band.maxLevel(),
        band.dailyServiceHours(),
        band.priorityOnly());
  }

  @Override
  public Class<GenerateProposalCommand> commandType() {
    return GenerateProposalCommand.class;
  }
}
