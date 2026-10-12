package co.caudal.domain.schedule;

import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The hours of service a day allows (docs/Reglas-de-negocio.md, section 3): the least of the hours
 * of the band, the length of the operating window and {@code max_daily_service_hours}, cut by the
 * reserve guard when the low end of the forecast ({@code p10}) falls under {@code reserve_level}.
 *
 * @param hours hours to hand out
 * @param windowStart start of the operating window of the day
 * @param priorityOnly true when only priority sectors get water (band or reserve policy)
 * @param reserveCut true when the reserve guard cut the hours
 */
public record DailyHours(
    BigDecimal hours, Instant windowStart, boolean priorityOnly, boolean reserveCut) {

  /** No cut when the forecast falls under the reserve. */
  public static final String POLICY_NONE = "NONE";

  /** Hours limited to those of the critical band. */
  public static final String POLICY_CRITICAL_BAND = "REDUCE_TO_CRITICAL_BAND";

  /** Only priority sectors get water. */
  public static final String POLICY_PRIORITY_ONLY = "PRIORITY_ONLY";

  private static final String CRITICAL = "CRITICAL";
  private static final int SCALE = 2;
  private static final BigDecimal SECONDS_PER_HOUR =
      BigDecimal.valueOf(Duration.ofHours(1).toSeconds());

  /**
   * Computes the hours of a day.
   *
   * @param band the band of the last observed level
   * @param bands every band of the rules, to find the critical one
   * @param windows the operating windows of the rules
   * @param day the day of service
   * @param zone time zone of the aqueduct
   * @param maxDailyHours {@code max_daily_service_hours}
   * @param reserve the forecast and the reserve of the rules
   * @return the hours, or empty when no window opens that day
   */
  public static Optional<DailyHours> compute(
      LevelBand band,
      List<LevelBand> bands,
      List<OperatingWindow> windows,
      LocalDate day,
      ZoneId zone,
      BigDecimal maxDailyHours,
      ReserveGuard reserve) {
    BigDecimal p10 = reserve.p10();
    Optional<OperatingWindow> window = windowOf(windows, day.getDayOfWeek());
    if (window.isEmpty()) {
      return Optional.empty();
    }
    LocalTime start = window.get().start();
    BigDecimal windowHours =
        BigDecimal.valueOf(Duration.between(start, window.get().end()).toSeconds())
            .divide(SECONDS_PER_HOUR, SCALE, RoundingMode.DOWN);
    BigDecimal hours = band.dailyServiceHours().min(windowHours).min(maxDailyHours);
    boolean priorityOnly = band.priorityOnly();
    boolean cut = false;
    if (p10 != null && p10.compareTo(reserve.reserveLevel()) < 0) {
      if (POLICY_CRITICAL_BAND.equals(reserve.policy())) {
        BigDecimal critical =
            bands.stream()
                .filter(b -> CRITICAL.equals(b.band()))
                .map(LevelBand::dailyServiceHours)
                .findFirst()
                .orElse(hours);
        cut = critical.compareTo(hours) < 0;
        hours = hours.min(critical);
      } else if (POLICY_PRIORITY_ONLY.equals(reserve.policy())) {
        cut = !priorityOnly;
        priorityOnly = true;
      }
    }
    return Optional.of(
        new DailyHours(hours, day.atTime(start).atZone(zone).toInstant(), priorityOnly, cut));
  }

  /**
   * The reserve the Board wants to keep and the forecast compared with it.
   *
   * @param p10 low end of the forecast of the day, or null without forecast
   * @param reserveLevel {@code reserve_level}
   * @param policy {@code reserve_policy}
   */
  public record ReserveGuard(BigDecimal p10, BigDecimal reserveLevel, String policy) {}

  /** The window of a weekday wins over the windows of every day; the earliest one is used. */
  static Optional<OperatingWindow> windowOf(List<OperatingWindow> windows, DayOfWeek day) {
    Comparator<OperatingWindow> earliest = Comparator.comparing(OperatingWindow::start);
    Optional<OperatingWindow> specific =
        windows.stream()
            .filter(w -> w.dayOfWeek() != null && w.dayOfWeek() == day.getValue())
            .min(earliest);
    return specific.isPresent()
        ? specific
        : windows.stream().filter(w -> w.dayOfWeek() == null).min(earliest);
  }
}
