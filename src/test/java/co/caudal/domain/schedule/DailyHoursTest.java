package co.caudal.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.rules.RuleSet.LevelBand;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailyHoursTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final LocalDate DAY = LocalDate.of(2026, 10, 9);
  private static final LevelBand CRITICAL =
      new LevelBand(
          "CRITICAL", BigDecimal.ZERO, new BigDecimal("1.50"), new BigDecimal("3.00"), true);
  private static final LevelBand LOW =
      new LevelBand(
          "LOW", new BigDecimal("1.50"), new BigDecimal("3.50"), new BigDecimal("8.00"), false);
  private static final List<OperatingWindow> EVERY_DAY =
      List.of(new OperatingWindow(null, LocalTime.of(5, 0), LocalTime.of(21, 0)));

  private static Optional<DailyHours> hours(
      LevelBand band, List<OperatingWindow> windows, String p10, String policy) {
    return DailyHours.compute(
        band,
        List.of(CRITICAL, LOW),
        windows,
        DAY,
        BOGOTA,
        new BigDecimal("16.00"),
        new DailyHours.ReserveGuard(
            p10 == null ? null : new BigDecimal(p10), new BigDecimal("1.00"), policy));
  }

  @Test
  void theBandLimitsTheExampleDayToEightHoursFromFive() {
    DailyHours day = hours(LOW, EVERY_DAY, "1.80", "REDUCE_TO_CRITICAL_BAND").orElseThrow();

    assertThat(day.hours()).isEqualByComparingTo("8.00");
    assertThat(day.windowStart()).isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));
    assertThat(day.reserveCut()).isFalse();
    assertThat(day.priorityOnly()).isFalse();
  }

  @Test
  void aForecastUnderTheReserveCutsToTheCriticalBand() {
    DailyHours day = hours(LOW, EVERY_DAY, "0.90", "REDUCE_TO_CRITICAL_BAND").orElseThrow();

    assertThat(day.hours()).isEqualByComparingTo("3.00");
    assertThat(day.reserveCut()).isTrue();
  }

  @Test
  void otherPoliciesKeepTheHoursOrServeOnlyPrioritySectors() {
    assertThat(hours(LOW, EVERY_DAY, "0.90", "NONE").orElseThrow().hours())
        .isEqualByComparingTo("8.00");
    DailyHours priority = hours(LOW, EVERY_DAY, "0.90", "PRIORITY_ONLY").orElseThrow();
    assertThat(priority.priorityOnly()).isTrue();
    assertThat(priority.reserveCut()).isTrue();
    assertThat(hours(CRITICAL, EVERY_DAY, null, "NONE").orElseThrow().priorityOnly()).isTrue();
  }

  @Test
  void aShortWindowOfTheWeekdayWinsAndNoWindowMeansNoService() {
    List<OperatingWindow> friday =
        List.of(
            EVERY_DAY.getFirst(),
            new OperatingWindow(
                DAY.getDayOfWeek().getValue(), LocalTime.of(6, 0), LocalTime.of(10, 30)));

    DailyHours day = hours(LOW, friday, null, "NONE").orElseThrow();

    assertThat(day.hours()).isEqualByComparingTo("4.50");
    assertThat(
            hours(
                LOW,
                List.of(new OperatingWindow(1, LocalTime.of(5, 0), LocalTime.of(6, 0))),
                null,
                "NONE"))
        .isEmpty();
  }
}
