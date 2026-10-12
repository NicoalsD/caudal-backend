package co.caudal.api.mapper;

import co.caudal.api.dto.request.UpdateRuleSetRequest;
import co.caudal.api.dto.response.RuleSetResponse;
import co.caudal.api.dto.response.RuleSetSummaryResponse;
import co.caudal.api.dto.response.RuleSetVersionResponse;
import co.caudal.application.rules.GetCurrentRuleSetUseCase.CurrentRuleSet;
import co.caudal.application.rules.RuleSetChanges;
import co.caudal.application.rules.RuleSetDetail;
import co.caudal.application.rules.RuleSetSummary;
import co.caudal.application.rules.RuleSetView;
import java.util.List;

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

  /**
   * Maps a version in a list.
   *
   * @param summary the version
   * @return the response
   */
  public static RuleSetSummaryResponse toResponse(RuleSetSummary summary) {
    return new RuleSetSummaryResponse(
        summary.id(),
        summary.version(),
        summary.status(),
        summary.validFrom(),
        summary.validTo(),
        summary.activatedAt(),
        summary.basedOnRuleSetId());
  }

  /**
   * Maps a full version with its history.
   *
   * @param detail the version
   * @return the response
   */
  public static RuleSetVersionResponse toResponse(RuleSetDetail detail) {
    RuleSetView r = detail.rules();
    return new RuleSetVersionResponse(
        r.id(),
        r.version(),
        detail.status(),
        detail.basedOnRuleSetId(),
        r.validFrom(),
        detail.validTo(),
        detail.changeReason(),
        detail.createdBy(),
        detail.createdAt(),
        detail.activatedBy(),
        detail.activatedAt(),
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
        bands(r),
        sectors(r),
        valveOrder(r),
        windows(r));
  }

  private static List<RuleSetResponse.Band> bands(RuleSetView r) {
    return r.bands().stream()
        .map(
            b ->
                new RuleSetResponse.Band(
                    b.band(), b.minLevel(), b.maxLevel(), b.dailyServiceHours(), b.priorityOnly()))
        .toList();
  }

  private static List<RuleSetResponse.SectorSetting> sectors(RuleSetView r) {
    return r.sectors().stream()
        .map(
            s ->
                new RuleSetResponse.SectorSetting(
                    s.sectorId(), s.included(), s.priority(), s.priorityRank()))
        .toList();
  }

  private static List<RuleSetResponse.ValveOrder> valveOrder(RuleSetView r) {
    return r.valveOrder().stream()
        .map(v -> new RuleSetResponse.ValveOrder(v.valveId(), v.sequenceOrder()))
        .toList();
  }

  private static List<RuleSetResponse.OperatingWindow> windows(RuleSetView r) {
    return r.operatingWindows().stream()
        .map(w -> new RuleSetResponse.OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
        .toList();
  }

  /**
   * Maps the changes of a draft.
   *
   * @param request the body
   * @return the changes
   */
  public static RuleSetChanges toChanges(UpdateRuleSetRequest request) {
    return new RuleSetChanges(
        request.changeReason(),
        request.reserveLevel(),
        request.reservePolicy(),
        request.maxDailyServiceHours(),
        request.minShiftHours(),
        request.maxShiftHours(),
        request.allocationStrategy(),
        request.duplicateWindowMinutes(),
        request.maxLevelChangePerHour(),
        request.staleReadingHours(),
        request.maxBackdateDays(),
        request.forecastHorizonDays(),
        request.forecastContextDays(),
        request.trendThresholdPerDay(),
        request.bands() == null
            ? null
            : request.bands().stream()
                .map(
                    b ->
                        new RuleSetView.BandView(
                            b.band(),
                            b.minLevel(),
                            b.maxLevel(),
                            b.dailyServiceHours(),
                            Boolean.TRUE.equals(b.priorityOnly())))
                .toList(),
        request.sectors() == null
            ? null
            : request.sectors().stream()
                .map(
                    s ->
                        new RuleSetView.SectorSettingView(
                            s.sectorId(),
                            !Boolean.FALSE.equals(s.isIncluded()),
                            Boolean.TRUE.equals(s.isPriority()),
                            s.priorityRank()))
                .toList(),
        request.valveOrder() == null
            ? null
            : request.valveOrder().stream()
                .map(v -> new RuleSetView.ValveOrderView(v.valveId(), v.sequenceOrder()))
                .toList(),
        request.operatingWindows() == null
            ? null
            : request.operatingWindows().stream()
                .map(w -> new RuleSetView.WindowView(w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList());
  }
}
