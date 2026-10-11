package co.caudal.application.rules;

import co.caudal.domain.rules.RuleSet;
import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.domain.rules.RuleSet.Parameters;
import co.caudal.domain.rules.RuleSet.SectorSetting;
import co.caudal.domain.rules.RuleSet.ValveOrder;

/** Turns the stored view of a version into the domain {@link RuleSet}. */
final class RuleSetConversion {

  private RuleSetConversion() {}

  static RuleSet toDomain(RuleSetDetail detail) {
    RuleSetView r = detail.rules();
    return new RuleSet(
        r.id(),
        r.version(),
        detail.status(),
        detail.basedOnRuleSetId(),
        new Parameters(
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
            r.trendThresholdPerDay()),
        r.bands().stream()
            .map(
                b ->
                    new LevelBand(
                        b.band(),
                        b.minLevel(),
                        b.maxLevel(),
                        b.dailyServiceHours(),
                        b.priorityOnly()))
            .toList(),
        r.sectors().stream()
            .map(s -> new SectorSetting(s.sectorId(), s.included(), s.priority(), s.priorityRank()))
            .toList(),
        r.valveOrder().stream().map(v -> new ValveOrder(v.valveId(), v.sequenceOrder())).toList(),
        r.operatingWindows().stream()
            .map(w -> new OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
            .toList());
  }
}
