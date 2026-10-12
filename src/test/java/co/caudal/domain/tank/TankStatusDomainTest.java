package co.caudal.domain.tank;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.forecast.DailyLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TankStatusDomainTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.10");

  private static List<DailyLevel> days(String... values) {
    return java.util.stream.IntStream.range(0, values.length)
        .mapToObj(i -> new DailyLevel(LocalDate.of(2026, 10, 6 + i), new BigDecimal(values[i])))
        .toList();
  }

  @Test
  void theExampleOfTheRulesGoesDownAQuarterADay() {
    Trend trend =
        new LeastSquaresTrend().trend(days("2.60", "2.35", "2.10"), THRESHOLD).orElseThrow();

    assertThat(trend.slopePerDay()).isEqualByComparingTo("-0.25");
    assertThat(trend.label()).isEqualTo("DOWN");
    assertThat(trend.labelEs()).isEqualTo("viene bajando");
  }

  @Test
  void smallChangesAreStableAndRisesAreUp() {
    LeastSquaresTrend strategy = new LeastSquaresTrend();

    assertThat(strategy.trend(days("2.10", "2.15", "2.12"), THRESHOLD).orElseThrow().label())
        .isEqualTo("STABLE");
    assertThat(strategy.trend(days("1.00", "1.50", "2.00"), THRESHOLD).orElseThrow().labelEs())
        .isEqualTo("sube");
    assertThat(strategy.trend(days("2.10"), THRESHOLD)).isEmpty();
  }

  @Test
  void aLevelOlderThanTheLimitIsStale() {
    Instant now = Instant.parse("2026-10-09T09:30:00Z");
    BigDecimal level = new BigDecimal("2.10");

    assertThat(TankLevelState.of(level, now.minusSeconds(10 * 3600 + 1800), now, 12))
        .isInstanceOf(TankLevelState.Fresh.class);
    TankLevelState old = TankLevelState.of(level, now.minusSeconds(13 * 3600), now, 12);
    assertThat(old.stale()).isTrue();
    assertThat(old.opensStaleAnomaly()).isTrue();
    assertThat(TankLevelState.of(null, null, now, 12).stale()).isFalse();
  }
}
