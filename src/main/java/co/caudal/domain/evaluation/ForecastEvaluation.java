package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs every metric over the comparable days of a horizon and decides whether the AI helps
 * (docs/Modulo-IA.md, section 9.3): positive skill, at least 30 forecasts evaluated and a coverage
 * between 70 % and 90 %. With fewer forecasts there is no verdict.
 */
public final class ForecastEvaluation {

  /** Fewest forecasts for a verdict (docs/Modulo-IA.md, 9.3). */
  public static final int MIN_SAMPLE = 30;

  /** Lowest calibrated coverage (docs/Modulo-IA.md, 9.3). */
  public static final BigDecimal MIN_COVERAGE = new BigDecimal("0.70");

  /** Highest calibrated coverage (docs/Modulo-IA.md, 9.3). */
  public static final BigDecimal MAX_COVERAGE = new BigDecimal("0.90");

  private static final int SCALE = 4;
  private static final List<EvaluationMetric> METRICS =
      List.of(
          MeanAbsoluteError.ofAi(),
          MeanAbsoluteError.ofSimple(),
          new WeightedQuantileLoss(),
          new IntervalCoverage(),
          new IntervalWidth(),
          new Skill());

  private ForecastEvaluation() {}

  /**
   * The result of a horizon.
   *
   * @param horizonDays the horizon
   * @param n forecasts evaluated
   * @param metrics each metric by code; a missing one could not be computed
   * @param helps true, false, or empty without enough evidence
   */
  public record Report(
      int horizonDays, int n, Map<String, BigDecimal> metrics, Optional<Boolean> helps) {

    /** Freezes the metrics. */
    public Report {
      metrics = Map.copyOf(metrics);
    }
  }

  /**
   * Evaluates a horizon.
   *
   * @param horizonDays the horizon
   * @param samples the comparable days
   * @return the report
   */
  public static Report evaluate(int horizonDays, List<EvaluationSample> samples) {
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    for (EvaluationMetric metric : METRICS) {
      metric
          .compute(samples)
          .ifPresent(v -> values.put(metric.code(), v.setScale(SCALE, RoundingMode.HALF_UP)));
    }
    return new Report(horizonDays, samples.size(), values, helps(samples.size(), values));
  }

  private static Optional<Boolean> helps(int n, Map<String, BigDecimal> values) {
    if (n < MIN_SAMPLE) {
      return Optional.empty();
    }
    BigDecimal skill = values.get("skill");
    BigDecimal coverage = values.get("coverage_80");
    boolean calibrated =
        coverage != null
            && coverage.compareTo(MIN_COVERAGE) >= 0
            && coverage.compareTo(MAX_COVERAGE) <= 0;
    return Optional.of(skill != null && skill.signum() > 0 && calibrated);
  }
}
