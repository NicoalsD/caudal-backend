package co.caudal.domain.schedule;

import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The limits a change of the Board must keep (docs/API.md, section 10.5): every shift lasts between
 * the minimum and the maximum of the rules, falls inside the operating window of the day, and all
 * of them together do not pass the hours available that day. Overlaps are refused by the database.
 */
public final class ShiftChangeRules {

  private static final int SCALE = 2;
  private static final BigDecimal SECONDS_PER_HOUR =
      BigDecimal.valueOf(Duration.ofHours(1).toSeconds());

  private ShiftChangeRules() {}

  /**
   * The limits of a day.
   *
   * @param day the day of service
   * @param zone time zone of the aqueduct
   * @param windows operating windows of the rules
   * @param availableHours hours available that day
   * @param minShiftHours shortest shift
   * @param maxShiftHours longest shift
   */
  public record DayLimits(
      LocalDate day,
      ZoneId zone,
      List<OperatingWindow> windows,
      BigDecimal availableHours,
      BigDecimal minShiftHours,
      BigDecimal maxShiftHours) {

    /** Copies the windows. */
    public DayLimits {
      windows = List.copyOf(windows);
    }
  }

  /**
   * Checks the shifts the Board leaves.
   *
   * @param shifts the shifts
   * @param limits the limits of the day
   * @throws DomainException SHIFT_LENGTH_INVALID, OUTSIDE_OPERATING_WINDOW or HOURS_EXCEEDED
   */
  public static void check(List<EditedShift> shifts, DayLimits limits) {
    Optional<OperatingWindow> window =
        DailyHours.windowOf(limits.windows(), limits.day().getDayOfWeek());
    if (window.isEmpty()) {
      throw new DomainException(ErrorCode.OUTSIDE_OPERATING_WINDOW);
    }
    Instant opens = limits.day().atTime(window.get().start()).atZone(limits.zone()).toInstant();
    Instant closes = limits.day().atTime(window.get().end()).atZone(limits.zone()).toInstant();
    BigDecimal total = BigDecimal.ZERO;
    for (int i = 0; i < shifts.size(); i++) {
      EditedShift shift = shifts.get(i);
      BigDecimal hours = hours(shift);
      if (hours.compareTo(limits.minShiftHours()) < 0
          || hours.compareTo(limits.maxShiftHours()) > 0) {
        throw new DomainException(
            ErrorCode.SHIFT_LENGTH_INVALID,
            Map.of("index", i, "min", limits.minShiftHours(), "max", limits.maxShiftHours()));
      }
      if (shift.start().isBefore(opens) || shift.end().isAfter(closes)) {
        throw new DomainException(ErrorCode.OUTSIDE_OPERATING_WINDOW, Map.of("index", i));
      }
      total = total.add(hours);
    }
    if (total.compareTo(limits.availableHours()) > 0) {
      throw new DomainException(
          ErrorCode.HOURS_EXCEEDED, Map.of("available_hours", limits.availableHours()));
    }
  }

  private static BigDecimal hours(EditedShift shift) {
    return BigDecimal.valueOf(Duration.between(shift.start(), shift.end()).toSeconds())
        .divide(SECONDS_PER_HOUR, SCALE, RoundingMode.HALF_UP);
  }
}
