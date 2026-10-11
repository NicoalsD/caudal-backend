package co.caudal.application.rules;

import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.application.rules.RuleSetView.SectorSettingView;
import co.caudal.application.rules.RuleSetView.ValveOrderView;
import co.caudal.application.rules.RuleSetView.WindowView;
import co.caudal.domain.rules.RuleSet;
import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.domain.rules.RuleSet.Parameters;
import co.caudal.domain.rules.RuleSet.SectorSetting;
import co.caudal.domain.rules.RuleSet.ValveOrder;
import java.math.BigDecimal;
import java.util.List;

/**
 * What a member of the Board changes in a draft (docs/API.md, section 6.5). A null field keeps the
 * current value; a list replaces the current one.
 *
 * @param changeReason why the rules change
 * @param reserveLevel minimum reserve
 * @param reservePolicy reserve policy
 * @param maxDailyServiceHours daily cap
 * @param minShiftHours shortest shift
 * @param maxShiftHours longest shift
 * @param allocationStrategy allocation strategy
 * @param duplicateWindowMinutes window of duplicate readings
 * @param maxLevelChangePerHour sudden jump threshold
 * @param staleReadingHours age of a stale reading
 * @param maxBackdateDays oldest reading accepted
 * @param forecastHorizonDays forecast horizon
 * @param forecastContextDays forecast history
 * @param trendThresholdPerDay trend threshold
 * @param bands new level bands
 * @param sectors new sector settings
 * @param valveOrder new valve order
 * @param windows new operating windows
 */
public record RuleSetChanges(
    String changeReason,
    BigDecimal reserveLevel,
    String reservePolicy,
    BigDecimal maxDailyServiceHours,
    BigDecimal minShiftHours,
    BigDecimal maxShiftHours,
    String allocationStrategy,
    Integer duplicateWindowMinutes,
    BigDecimal maxLevelChangePerHour,
    Integer staleReadingHours,
    Integer maxBackdateDays,
    Integer forecastHorizonDays,
    Integer forecastContextDays,
    BigDecimal trendThresholdPerDay,
    List<BandView> bands,
    List<SectorSettingView> sectors,
    List<ValveOrderView> valveOrder,
    List<WindowView> windows) {

  /**
   * Applies the changes to a draft.
   *
   * @param draft the current draft
   * @return the draft with the changes
   */
  public RuleSet applyTo(RuleSet draft) {
    Parameters p = draft.parameters();
    Parameters merged =
        new Parameters(
            pick(reserveLevel, p.reserveLevel()),
            pick(reservePolicy, p.reservePolicy()),
            pick(maxDailyServiceHours, p.maxDailyServiceHours()),
            pick(minShiftHours, p.minShiftHours()),
            pick(maxShiftHours, p.maxShiftHours()),
            pick(allocationStrategy, p.allocationStrategy()),
            pick(duplicateWindowMinutes, p.duplicateWindowMinutes()),
            pick(maxLevelChangePerHour, p.maxLevelChangePerHour()),
            pick(staleReadingHours, p.staleReadingHours()),
            pick(maxBackdateDays, p.maxBackdateDays()),
            pick(forecastHorizonDays, p.forecastHorizonDays()),
            pick(forecastContextDays, p.forecastContextDays()),
            pick(trendThresholdPerDay, p.trendThresholdPerDay()));
    return new RuleSet(
        draft.id(),
        draft.version(),
        draft.status(),
        draft.basedOnRuleSetId(),
        merged,
        bands == null ? draft.bands() : bands.stream().map(RuleSetChanges::band).toList(),
        sectors == null ? draft.sectors() : sectors.stream().map(RuleSetChanges::sector).toList(),
        valveOrder == null
            ? draft.valveOrder()
            : valveOrder.stream().map(v -> new ValveOrder(v.valveId(), v.sequenceOrder())).toList(),
        windows == null
            ? draft.windows()
            : windows.stream()
                .map(w -> new OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList());
  }

  /**
   * Tells whether any child list is replaced.
   *
   * @return true if bands, sectors, valve order or windows come in the changes
   */
  public boolean replacesChildren() {
    return bands != null || sectors != null || valveOrder != null || windows != null;
  }

  private static LevelBand band(BandView b) {
    return new LevelBand(
        b.band(), b.minLevel(), b.maxLevel(), b.dailyServiceHours(), b.priorityOnly());
  }

  private static SectorSetting sector(SectorSettingView s) {
    return new SectorSetting(s.sectorId(), s.included(), s.priority(), s.priorityRank());
  }

  private static <T> T pick(T changed, T current) {
    return changed == null ? current : changed;
  }
}
