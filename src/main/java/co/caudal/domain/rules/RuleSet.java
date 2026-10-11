package co.caudal.domain.rules;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A version of the Board's rules (docs/Reglas-de-negocio.md, section 1): the parameters of the row
 * plus its level bands, sector settings, valve order and operating windows.
 *
 * <p>A new version is never written from scratch: {@link #copyForNewVersion(int)} clones the
 * version it starts from, children included, and the Board changes only what it needs. The original
 * stays as it was, so past decisions can still be explained with the rules of their day.
 *
 * @param id the version, null until stored
 * @param version its number in the aqueduct
 * @param status {@code DRAFT}, {@code ACTIVE}, {@code SUPERSEDED} or {@code DISCARDED}
 * @param basedOnRuleSetId the version it was copied from, null for the first
 * @param parameters the scalar parameters
 * @param bands the level bands
 * @param sectors the settings of each sector
 * @param valveOrder the order in which the valves open
 * @param windows the operating windows
 * @pattern P05 Prototype
 */
public record RuleSet(
    UUID id,
    int version,
    String status,
    UUID basedOnRuleSetId,
    Parameters parameters,
    List<LevelBand> bands,
    List<SectorSetting> sectors,
    List<ValveOrder> valveOrder,
    List<OperatingWindow> windows) {

  /** Status of the only editable version. */
  public static final String DRAFT = "DRAFT";

  /** Copies the lists so a version cannot change after it is built. */
  public RuleSet {
    Objects.requireNonNull(parameters, "parameters");
    bands = List.copyOf(bands);
    sectors = List.copyOf(sectors);
    valveOrder = List.copyOf(valveOrder);
    windows = List.copyOf(windows);
  }

  /**
   * Clones this version into a new draft: same parameters and children, the next number, and a link
   * to this version.
   *
   * @param newVersion the number of the new version
   * @return the draft, without id until it is stored
   * @throws IllegalArgumentException if the number does not come after this one
   */
  public RuleSet copyForNewVersion(int newVersion) {
    if (newVersion <= version) {
      throw new IllegalArgumentException("A new version must have a higher number");
    }
    return new RuleSet(
        null, newVersion, DRAFT, id, parameters, bands, sectors, valveOrder, windows);
  }

  /**
   * Tells whether the version can still be edited.
   *
   * @return true for a draft
   */
  public boolean isDraft() {
    return DRAFT.equals(status);
  }

  /**
   * Scalar parameters of a version.
   *
   * @param reserveLevel minimum reserve
   * @param reservePolicy what to do when the forecast falls under the reserve
   * @param maxDailyServiceHours daily cap of service hours
   * @param minShiftHours shortest shift
   * @param maxShiftHours longest shift
   * @param allocationStrategy how shifts are allocated
   * @param duplicateWindowMinutes window to flag repeated readings
   * @param maxLevelChangePerHour change per hour that flags a sudden jump
   * @param staleReadingHours age after which a reading is stale
   * @param maxBackdateDays oldest reading accepted
   * @param forecastHorizonDays days the forecast looks ahead
   * @param forecastContextDays days of history the forecast uses
   * @param trendThresholdPerDay change per day that marks a trend
   */
  public record Parameters(
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
      BigDecimal trendThresholdPerDay) {}

  /**
   * A level band, the interval {@code [minLevel, maxLevel)}.
   *
   * @param band {@code HIGH}, {@code LOW} or {@code CRITICAL}
   * @param minLevel lower limit, included
   * @param maxLevel upper limit, excluded (included for the top band)
   * @param dailyServiceHours service hours per day in the band
   * @param priorityOnly true if only priority sectors get water
   */
  public record LevelBand(
      String band,
      BigDecimal minLevel,
      BigDecimal maxLevel,
      BigDecimal dailyServiceHours,
      boolean priorityOnly) {}

  /**
   * How a sector takes part in the shifts.
   *
   * @param sectorId the sector
   * @param included true if it gets shifts
   * @param priority true if it goes first
   * @param priorityRank order among priority sectors, or null
   */
  public record SectorSetting(
      UUID sectorId, boolean included, boolean priority, Integer priorityRank) {}

  /**
   * Position of a valve in the opening sequence.
   *
   * @param valveId the valve
   * @param sequenceOrder its position, from 1
   */
  public record ValveOrder(UUID valveId, int sequenceOrder) {}

  /**
   * A window of local time when valves may be operated.
   *
   * @param dayOfWeek ISO day (1 Monday to 7 Sunday), or null for every day
   * @param start start time
   * @param end end time
   */
  public record OperatingWindow(Integer dayOfWeek, LocalTime start, LocalTime end) {}
}
