package co.caudal.application.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.domain.forecast.DailyLevel;
import co.caudal.domain.forecast.NaivePersistenceStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShadowBaselineForecasterTest {

  private static final ModelForecast MODEL =
      new ModelForecast("autogluon/chronos-2-small", "2.0.0", List.of(), 10);
  private static final ForecastRequest REQUEST =
      new ForecastRequest(
          new ForecastSeries(
              UUID.randomUUID(),
              List.of(new co.caudal.application.reading.LevelPoint(BigDecimal.ONE, Instant.EPOCH)),
              1,
              ZoneId.of("America/Bogota")),
          List.of(new DailyLevel(LocalDate.of(2026, 10, 1), BigDecimal.ONE)),
          BigDecimal.ZERO,
          new BigDecimal("5.00"));
  private final BaselineForecaster baseline =
      new BaselineForecaster(new NaivePersistenceStrategy());

  @Test
  void aForecastOfTheServiceCarriesTheSimpleEstimateInTheShadow() {
    ForecastOutcome outcome =
        new ShadowBaselineForecaster(r -> new ForecastOutcome(MODEL, false, null, null), baseline)
            .forecast(REQUEST);

    assertThat(outcome.primary()).isEqualTo(MODEL);
    assertThat(outcome.shadow().modelName()).isEqualTo("naive-persistence");
    assertThat(outcome.shadow().points()).hasSize(1);
  }

  @Test
  void aReplacementHasNoShadow() {
    ForecastOutcome replaced = new ForecastOutcome(MODEL, true, "IA_TIMEOUT", null);

    assertThat(new ShadowBaselineForecaster(r -> replaced, baseline).forecast(REQUEST))
        .isSameAs(replaced);
  }
}
