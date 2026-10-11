package co.caudal.domain.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NaivePersistenceStrategyTest {

  private static final BigDecimal MIN = BigDecimal.ZERO;
  private static final BigDecimal MAX = new BigDecimal("5.00");
  private static final LocalDate START = LocalDate.of(2026, 10, 1);
  private final NaivePersistenceStrategy strategy = new NaivePersistenceStrategy();

  private static List<DailyLevel> history(String... values) {
    List<DailyLevel> history = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      history.add(new DailyLevel(START.plusDays(i), new BigDecimal(values[i])));
    }
    return history;
  }

  @Test
  void aSteadyDropGivesTheSameDropAsCentreAndRange() {
    BaselineForecast forecast =
        strategy.forecast(history("3.00", "2.75", "2.50", "2.25", "2.00"), 2, MIN, MAX);

    assertThat(forecast.insufficientHistory()).isFalse();
    BaselineForecast.Point first = forecast.points().getFirst();
    assertThat(first.day()).isEqualTo(START.plusDays(5));
    assertThat(first.p50()).isEqualByComparingTo("1.75");
    assertThat(first.p10()).isEqualByComparingTo("1.75");
    assertThat(forecast.points().get(1).p50()).isEqualByComparingTo("1.50");
    assertThat(strategy.modelName()).isEqualTo("naive-persistence");
    assertThat(strategy.modelVersion()).isEqualTo("1");
  }

  @Test
  void theRangeComesFromTheEmpiricalQuantilesAndStaysOrdered() {
    BaselineForecast forecast =
        strategy.forecast(history("2.00", "2.10", "1.90", "2.20", "1.80", "2.00"), 1, MIN, MAX);

    BaselineForecast.Point point = forecast.points().getFirst();
    assertThat(point.p10()).isLessThan(point.p50());
    assertThat(point.p50()).isLessThan(point.p90());
    assertThat(point.p10()).isEqualByComparingTo("1.68");
    assertThat(point.p90()).isEqualByComparingTo("2.26");
  }

  @Test
  void withTooLittleHistoryEveryPointIsTheLastLevel() {
    BaselineForecast forecast = strategy.forecast(history("2.40", "2.30"), 3, MIN, MAX);

    assertThat(forecast.insufficientHistory()).isTrue();
    assertThat(forecast.points())
        .hasSize(3)
        .allSatisfy(
            p -> {
              assertThat(p.p10()).isEqualByComparingTo("2.30");
              assertThat(p.p90()).isEqualByComparingTo("2.30");
            });
  }

  @Test
  void longHorizonsWithoutEnoughSpansScaleTheDailyChangeAndStayOnTheGauge() {
    BaselineForecast forecast =
        strategy.forecast(history("1.00", "0.70", "0.40", "0.10"), 3, MIN, MAX);

    assertThat(forecast.points().get(2).p50()).isEqualByComparingTo("0.00");
    assertThat(NaivePersistenceStrategy.changes(history("1", "2", "4"), 2)).containsExactly(3.0);
    assertThatThrownBy(() -> strategy.forecast(List.of(), 1, MIN, MAX))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
