package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftChangeRulesTest {

  static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  static final LocalDate DAY = LocalDate.of(2026, 10, 9);
  static final ShiftChangeRules.DayLimits LIMITS =
      new ShiftChangeRules.DayLimits(
          DAY,
          BOGOTA,
          List.of(new OperatingWindow(null, LocalTime.of(5, 0), LocalTime.of(21, 0))),
          new BigDecimal("8.00"),
          new BigDecimal("1.00"),
          new BigDecimal("4.00"));

  static EditedShift shift(int fromHour, int toHour) {
    Instant start = DAY.atTime(fromHour, 0).atZone(BOGOTA).toInstant();
    Instant end = DAY.atTime(toHour, 0).atZone(BOGOTA).toInstant();
    return new EditedShift(UUID.randomUUID(), start, end);
  }

  static void refused(List<EditedShift> shifts, ShiftChangeRules.DayLimits limits, ErrorCode code) {
    assertThatThrownBy(() -> ShiftChangeRules.check(shifts, limits))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void shiftsInsideTheLimitsPass() {
    assertThatCode(() -> ShiftChangeRules.check(List.of(shift(7, 10), shift(10, 14)), LIMITS))
        .doesNotThrowAnyException();
  }

  @Test
  void aShiftTooShortOrTooLongIsRefused() {
    refused(
        List.of(new EditedShift(UUID.randomUUID(), shift(7, 8).start(), shift(7, 8).start())),
        LIMITS,
        ErrorCode.SHIFT_LENGTH_INVALID);
    refused(List.of(shift(7, 12)), LIMITS, ErrorCode.SHIFT_LENGTH_INVALID);
  }

  @Test
  void aShiftOutsideTheWindowIsRefused() {
    refused(List.of(shift(4, 6)), LIMITS, ErrorCode.OUTSIDE_OPERATING_WINDOW);
    refused(List.of(shift(19, 22)), LIMITS, ErrorCode.OUTSIDE_OPERATING_WINDOW);
    ShiftChangeRules.DayLimits sundayOnly =
        new ShiftChangeRules.DayLimits(
            DAY,
            BOGOTA,
            List.of(new OperatingWindow(7, LocalTime.of(5, 0), LocalTime.of(21, 0))),
            LIMITS.availableHours(),
            LIMITS.minShiftHours(),
            LIMITS.maxShiftHours());
    refused(List.of(shift(7, 9)), sundayOnly, ErrorCode.OUTSIDE_OPERATING_WINDOW);
  }

  @Test
  void moreHoursThanAvailableAreRefused() {
    refused(List.of(shift(5, 9), shift(9, 13), shift(13, 14)), LIMITS, ErrorCode.HOURS_EXCEEDED);
  }
}
