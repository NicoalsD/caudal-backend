package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Mean width of the range: {@code (1/n) Σ (p90 - p10)}.
 *
 * @pattern P19 Strategy
 */
public final class IntervalWidth implements EvaluationMetric {

  @Override
  public String code() {
    return "avg_interval_width";
  }

  @Override
  public Optional<BigDecimal> compute(List<EvaluationSample> samples) {
    if (samples.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal sum =
        samples.stream()
            .map(s -> s.p90().subtract(s.p10()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Optional.of(EvaluationMetric.mean(sum, samples.size()));
  }
}
