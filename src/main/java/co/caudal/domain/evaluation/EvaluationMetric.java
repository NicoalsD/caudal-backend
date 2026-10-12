package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

/**
 * One way to measure the forecasts against the real levels (docs/Modulo-IA.md, section 9.2). A new
 * metric is a new strategy; the evaluation runs them all the same way.
 *
 * @pattern P19 Strategy
 */
public interface EvaluationMetric {

  /** Precision of the divisions. */
  MathContext PRECISION = MathContext.DECIMAL64;

  /**
   * The name of the metric in the answer.
   *
   * @return for example "mae_ia"
   */
  String code();

  /**
   * Computes the metric.
   *
   * @param samples the comparable days
   * @return the value, or empty when it cannot be computed (no samples or a zero denominator)
   */
  Optional<BigDecimal> compute(List<EvaluationSample> samples);

  /**
   * The mean of some values.
   *
   * @param sum their sum
   * @param n how many
   * @return the mean
   */
  static BigDecimal mean(BigDecimal sum, int n) {
    return sum.divide(BigDecimal.valueOf(n), PRECISION);
  }
}
