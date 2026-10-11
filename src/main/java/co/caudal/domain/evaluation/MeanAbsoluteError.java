package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Mean absolute error of a middle value: {@code (1/n) Σ |y - p50|}.
 *
 * @pattern P19 Strategy
 */
public final class MeanAbsoluteError implements EvaluationMetric {

  private final String code;
  private final Function<EvaluationSample, BigDecimal> forecast;

  private MeanAbsoluteError(String code, Function<EvaluationSample, BigDecimal> forecast) {
    this.code = code;
    this.forecast = forecast;
  }

  /**
   * The error of the AI.
   *
   * @return the metric
   */
  public static MeanAbsoluteError ofAi() {
    return new MeanAbsoluteError("mae_ia", EvaluationSample::p50);
  }

  /**
   * The error of the simple estimate.
   *
   * @return the metric
   */
  public static MeanAbsoluteError ofSimple() {
    return new MeanAbsoluteError("mae_simple", EvaluationSample::simpleP50);
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Optional<BigDecimal> compute(List<EvaluationSample> samples) {
    if (samples.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal sum =
        samples.stream()
            .map(s -> s.actual().subtract(forecast.apply(s)).abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Optional.of(EvaluationMetric.mean(sum, samples.size()));
  }
}
