package co.caudal.application.autopilot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The state of the aqueduct that the backend sends to {@code caudal-ia} (docs/Contrato-IA.md,
 * insights section). The lists are cut to the limits of the contract.
 *
 * @param aqueductId the aqueduct
 * @param aqueductName its name
 * @param zone the time zone in which the moments are written
 * @param generatedAt when the snapshot was taken
 * @param mode the mode of the autopilot
 * @param reserveFloorPct the reserve floor, in percent
 * @param operatorLastActionAt the last human action, or null
 * @param tank the tank
 * @param forecast the latest forecast, possibly empty
 * @param valves the motorized valves
 * @param schedule the shifts around now
 * @param anomalies the open anomalies
 */
public record InsightsSnapshot(
    UUID aqueductId,
    String aqueductName,
    ZoneId zone,
    Instant generatedAt,
    String mode,
    BigDecimal reserveFloorPct,
    Instant operatorLastActionAt,
    Tank tank,
    List<ForecastPoint> forecast,
    List<Valve> valves,
    List<Shift> schedule,
    List<Anomaly> anomalies) {

  /** Freezes the lists. */
  public InsightsSnapshot {
    forecast = List.copyOf(forecast);
    valves = List.copyOf(valves);
    schedule = List.copyOf(schedule);
    anomalies = List.copyOf(anomalies);
  }

  /**
   * The tank.
   *
   * @param id the tank
   * @param name its name
   * @param levelM the last effective level, in meters of gauge, or null
   * @param maxLevelM the top of the gauge, in meters
   * @param levelPct the level in percent of the gauge, or null
   * @param band the band of the rules, or null
   * @param observedAt when the level was read, or null
   * @param trend RISING, FALLING or STABLE, or null
   */
  public record Tank(
      UUID id,
      String name,
      BigDecimal levelM,
      BigDecimal maxLevelM,
      BigDecimal levelPct,
      String band,
      Instant observedAt,
      String trend) {}

  /**
   * A forecast day.
   *
   * @param timestamp the start of the day
   * @param p10 low quantile
   * @param p50 median
   * @param p90 high quantile
   */
  public record ForecastPoint(Instant timestamp, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}

  /**
   * A valve.
   *
   * @param id the valve
   * @param sectorId its sector
   * @param sectorName the name of the sector
   * @param regionName the name of its region (the root sector), or null
   * @param state OPEN, CLOSED or UNKNOWN
   * @param motorized whether it can be moved by a command
   * @param priority its place in the opening order, 1 first
   */
  public record Valve(
      UUID id,
      UUID sectorId,
      String sectorName,
      String regionName,
      String state,
      boolean motorized,
      int priority) {}

  /**
   * A shift.
   *
   * @param sectorId the sector served
   * @param startsAt start
   * @param endsAt end
   * @param status PLANNED, DONE, PARTIAL or SKIPPED
   */
  public record Shift(UUID sectorId, Instant startsAt, Instant endsAt, String status) {}

  /**
   * An open anomaly.
   *
   * @param kind its kind
   * @param severity LOW, MEDIUM or HIGH
   */
  public record Anomaly(String kind, String severity) {}
}
