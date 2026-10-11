package co.caudal.application.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.application.reading.LevelPoint;
import co.caudal.domain.forecast.DailyLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastRunnerTest {

  @Test
  void eachLocalDayKeepsItsLastLevel() {
    ZoneId bogota = ZoneId.of("America/Bogota");
    List<DailyLevel> daily =
        ForecastRunner.daily(
            List.of(
                new LevelPoint(new BigDecimal("3.00"), Instant.parse("2026-10-01T12:00:00Z")),
                new LevelPoint(new BigDecimal("2.90"), Instant.parse("2026-10-01T22:00:00Z")),
                new LevelPoint(new BigDecimal("2.80"), Instant.parse("2026-10-02T04:00:00Z")),
                new LevelPoint(new BigDecimal("2.70"), Instant.parse("2026-10-02T12:00:00Z"))),
            bogota);

    assertThat(daily)
        .containsExactly(
            new DailyLevel(LocalDate.of(2026, 10, 1), new BigDecimal("2.80")),
            new DailyLevel(LocalDate.of(2026, 10, 2), new BigDecimal("2.70")));
  }
}
