package co.caudal.domain.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reasons of each shift, in the order they are shown (docs/Reglas-de-negocio.md, section 6):
 * priority, hours without service, band of the tank, and on the first shift of the day the forecast
 * and the reserve guard, which are facts of the whole day.
 */
public final class TurnExplainer {

  /** The facts of the day every explanation may use. */
  private final DayFacts day;

  private boolean firstDone;

  /**
   * The facts of the day.
   *
   * @param level the last observed level
   * @param band its band
   * @param bandHours hours of service of the day
   * @param p10 low end of the forecast, or null
   * @param p50 most likely level, or null
   * @param p90 high end, or null
   * @param forecastDay the day forecast, or null
   * @param reserveLevel the reserve of the rules
   * @param reserveCut whether the reserve cut the hours
   */
  public record DayFacts(
      BigDecimal level,
      String band,
      BigDecimal bandHours,
      BigDecimal p10,
      BigDecimal p50,
      BigDecimal p90,
      LocalDate forecastDay,
      BigDecimal reserveLevel,
      boolean reserveCut) {}

  /**
   * Creates the explainer of a day.
   *
   * @param day the facts of the day
   */
  public TurnExplainer(DayFacts day) {
    this.day = day;
  }

  /**
   * The reasons of the next shift. Call it once per shift, in order.
   *
   * @param sector the sector that gets the shift
   * @return the reasons
   */
  public List<TurnReason> explain(SectorCandidate sector) {
    List<TurnReason> reasons = new ArrayList<>();
    if (sector.priority()) {
      reasons.add(
          new TurnReason(
              "PRIORITY_SECTOR",
              params("sector", sector.name(), "priority_rank", sector.priorityRank())));
    }
    if (sector.hoursWithoutService() != null) {
      reasons.add(
          new TurnReason(
              "HOURS_WITHOUT_SERVICE",
              params(
                  "sector",
                  sector.name(),
                  "horas",
                  sector.hoursWithoutService(),
                  "origen",
                  sector.waitOrigin())));
    }
    reasons.add(
        new TurnReason(
            "TANK_BAND",
            params("nivel", day.level(), "banda", day.band(), "horas", day.bandHours())));
    if (!firstDone) {
      firstDone = true;
      if (day.p10() != null) {
        reasons.add(
            new TurnReason(
                "FORECAST_RANGE",
                params(
                    "p10",
                    day.p10(),
                    "p50",
                    day.p50(),
                    "p90",
                    day.p90(),
                    "dia",
                    day.forecastDay())));
        reasons.add(
            new TurnReason(
                "RESERVE_GUARD",
                params(
                    "p10",
                    day.p10(),
                    "reserve_level",
                    day.reserveLevel(),
                    "recortado",
                    day.reserveCut())));
      }
    }
    return reasons;
  }

  private static Map<String, Object> params(Object... keysAndValues) {
    Map<String, Object> params = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (keysAndValues[i + 1] != null) {
        params.put((String) keysAndValues[i], keysAndValues[i + 1].toString());
      }
    }
    return params;
  }
}
