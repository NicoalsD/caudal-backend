package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Weighted quantile loss of the AI over p10, p50 and p90: {@code (1/|Q|) Σ_q [2 Σ_t QL_q(y_t,
 * ŷ_t,q) / Σ_t |y_t|]}. It cannot be computed when every real level is zero.
 *
 * @pattern P19 Strategy
 */
public final class WeightedQuantileLoss implements EvaluationMetric {

  private static final BigDecimal TWO = BigDecimal.valueOf(2);
  private static final Map<BigDecimal, Function<EvaluationSample, BigDecimal>> QUANTILES =
      Map.of(
          new BigDecimal("0.1"), EvaluationSample::p10,
          new BigDecimal("0.5"), EvaluationSample::p50,
          new BigDecimal("0.9"), EvaluationSample::p90);

  @Override
  public String code() {
    return "wql_ia";
  }

  @Override
  public Optional<BigDecimal> compute(List<EvaluationSample> samples) {
    BigDecimal scale =
        samples.stream().map(s -> s.actual().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (samples.isEmpty() || scale.signum() == 0) {
      return Optional.empty();
    }
    BigDecimal total = BigDecimal.ZERO;
    for (Map.Entry<BigDecimal, Function<EvaluationSample, BigDecimal>> q : QUANTILES.entrySet()) {
      BigDecimal loss =
          samples.stream()
              .map(s -> loss(q.getKey(), s.actual(), q.getValue().apply(s)))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      total = total.add(TWO.multiply(loss).divide(scale, PRECISION));
    }
    return Optional.of(EvaluationMetric.mean(total, QUANTILES.size()));
  }

  /** Quantile loss of one value. */
  static BigDecimal loss(BigDecimal q, BigDecimal actual, BigDecimal forecast) {
    return actual.compareTo(forecast) >= 0
        ? q.multiply(actual.subtract(forecast))
        : BigDecimal.ONE.subtract(q).multiply(forecast.subtract(actual));
  }
}
