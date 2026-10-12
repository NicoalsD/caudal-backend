package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Share of real levels inside [p10, p90]. The goal is near 80 %.
 *
 * @pattern P19 Strategy
 */
public final class IntervalCoverage implements EvaluationMetric {

  @Override
  public String code() {
    return "coverage_80";
  }

  @Override
  public Optional<BigDecimal> compute(List<EvaluationSample> samples) {
    if (samples.isEmpty()) {
      return Optional.empty();
    }
    long inside =
        samples.stream()
            .filter(s -> s.actual().compareTo(s.p10()) >= 0 && s.actual().compareTo(s.p90()) <= 0)
            .count();
    return Optional.of(EvaluationMetric.mean(BigDecimal.valueOf(inside), samples.size()));
  }
}
