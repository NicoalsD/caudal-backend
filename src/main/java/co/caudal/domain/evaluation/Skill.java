package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Relative improvement over the simple estimate: {@code 1 - MAE_ia / MAE_simple}. Above zero, the
 * AI wins. It cannot be computed when the simple estimate made no error at all.
 *
 * @pattern P19 Strategy
 */
public final class Skill implements EvaluationMetric {

  private final EvaluationMetric ai = MeanAbsoluteError.ofAi();
  private final EvaluationMetric simple = MeanAbsoluteError.ofSimple();

  @Override
  public String code() {
    return "skill";
  }

  @Override
  public Optional<BigDecimal> compute(List<EvaluationSample> samples) {
    Optional<BigDecimal> base = simple.compute(samples).filter(mae -> mae.signum() != 0);
    if (base.isEmpty()) {
      return Optional.empty();
    }
    return ai.compute(samples)
        .map(mae -> BigDecimal.ONE.subtract(mae.divide(base.get(), PRECISION)));
  }
}
