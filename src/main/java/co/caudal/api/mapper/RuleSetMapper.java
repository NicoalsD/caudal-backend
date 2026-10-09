package co.caudal.api.mapper;

import co.caudal.api.dto.response.RuleSetResponse;
import co.caudal.application.rules.GetCurrentRuleSetUseCase.CurrentRuleSet;
import co.caudal.application.rules.RuleSetView;

/** Maps the rules to the response DTO. */
public final class RuleSetMapper {

  private RuleSetMapper() {}

  /**
   * Maps the current rules.
   *
   * @param current the rules, the gauge and the server time
   * @return the response
   */
  public static RuleSetResponse toResponse(CurrentRuleSet current) {
    RuleSetView r = current.rules();
    RuleSetResponse.Gauge gauge =
        current.gauge() == null
            ? null
            : new RuleSetResponse.Gauge(
                current.gauge().gaugeMin(),
                current.gauge().gaugeMax(),
                current.gauge().gaugeStep());
    return new RuleSetResponse(
        r.id(),
        r.version(),
        r.validFrom(),
        r.reserveLevel(),
        r.reservePolicy(),
        r.maxDailyServiceHours(),
        r.minShiftHours(),
        r.maxShiftHours(),
        r.allocationStrategy(),
        r.duplicateWindowMinutes(),
        r.maxLevelChangePerHour(),
        r.staleReadingHours(),
        r.maxBackdateDays(),
        r.forecastHorizonDays(),
        r.forecastContextDays(),
        r.trendThresholdPerDay(),
        r.bands().stream()
            .map(
                b ->
                    new RuleSetResponse.Band(
                        b.band(),
                        b.minLevel(),
                        b.maxLevel(),
                        b.dailyServiceHours(),
                        b.priorityOnly()))
            .toList(),
        r.sectors().stream()
            .map(
                s ->
                    new RuleSetResponse.SectorSetting(
                        s.sectorId(), s.included(), s.priority(), s.priorityRank()))
            .toList(),
        r.valveOrder().stream()
            .map(v -> new RuleSetResponse.ValveOrder(v.valveId(), v.sequenceOrder()))
            .toList(),
        r.operatingWindows().stream()
            .map(
                w -> new RuleSetResponse.OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
            .toList(),
        gauge,
        current.serverTime());
  }
}
