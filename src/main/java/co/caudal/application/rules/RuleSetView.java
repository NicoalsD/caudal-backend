package co.caudal.application.rules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A version of the Board's rules with its children, as the use cases read it.
 *
 * @param id the version id
 * @param version the version number
 * @param validFrom start of validity
 * @param reserveLevel minimum reserve in gauge units
 * @param reservePolicy what to do when the forecast falls under the reserve
 * @param maxDailyServiceHours maximum service hours per day
 * @param minShiftHours minimum duration of a shift
 * @param maxShiftHours maximum duration of a shift
 * @param allocationStrategy strategy code
 * @param duplicateWindowMinutes window to detect repeated readings
 * @param maxLevelChangePerHour maximum plausible change per hour
 * @param staleReadingHours hours after which a reading is old
 * @param maxBackdateDays maximum days a reading may be back dated
 * @param forecastHorizonDays forecast horizon in days
 * @param forecastContextDays days of history for the forecast
 * @param trendThresholdPerDay threshold to call a trend
 * @param bands level bands
 * @param sectors sector settings
 * @param valveOrder opening order of the valves
 * @param operatingWindows operating windows
 */
public record RuleSetView(
    UUID id,
    int version,
    Instant validFrom,
    BigDecimal reserveLevel,
    String reservePolicy,
    BigDecimal maxDailyServiceHours,
    BigDecimal minShiftHours,
    BigDecimal maxShiftHours,
    String allocationStrategy,
    int duplicateWindowMinutes,
    BigDecimal maxLevelChangePerHour,
    int staleReadingHours,
    int maxBackdateDays,
    int forecastHorizonDays,
    int forecastContextDays,
    BigDecimal trendThresholdPerDay,
    List<BandView> bands,
    List<SectorSettingView> sectors,
    List<ValveOrderView> valveOrder,
    List<WindowView> operatingWindows) {

  /**
   * A level band.
   *
   * @param band HIGH, LOW or CRITICAL
   * @param minLevel lower limit, included
   * @param maxLevel upper limit, excluded except for the top band
   * @param dailyServiceHours service hours per day
   * @param priorityOnly only priority sectors are served
   */
  public record BandView(
      String band,
      BigDecimal minLevel,
      BigDecimal maxLevel,
      BigDecimal dailyServiceHours,
      boolean priorityOnly) {}

  /**
   * Setting of a sector.
   *
   * @param sectorId the sector
   * @param included receives shifts
   * @param priority is a priority sector
   * @param priorityRank order among priority sectors
   */
  public record SectorSettingView(
      UUID sectorId, boolean included, boolean priority, Integer priorityRank) {}

  /**
   * Position of a valve in the opening sequence.
   *
   * @param valveId the valve
   * @param sequenceOrder the position
   */
  public record ValveOrderView(UUID valveId, int sequenceOrder) {}

  /**
   * An operating window.
   *
   * @param dayOfWeek ISO day (1 Monday), null for every day
   * @param startTime local start
   * @param endTime local end
   */
  public record WindowView(Integer dayOfWeek, LocalTime startTime, LocalTime endTime) {}
}
