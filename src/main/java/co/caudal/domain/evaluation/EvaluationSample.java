package co.caudal.domain.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One day where the forecast of the AI, the simple estimate and the real level can be compared
 * (docs/Modulo-IA.md, section 9.1).
 *
 * @param targetDate the day forecast
 * @param actual the real level of that day
 * @param p10 low end of the AI forecast
 * @param p50 middle of the AI forecast
 * @param p90 high end of the AI forecast
 * @param simpleP50 the simple estimate for the same day
 */
public record EvaluationSample(
    LocalDate targetDate,
    BigDecimal actual,
    BigDecimal p10,
    BigDecimal p50,
    BigDecimal p90,
    BigDecimal simpleP50) {

  /** Requires every value. */
  public EvaluationSample {
    Objects.requireNonNull(targetDate, "targetDate");
    Objects.requireNonNull(actual, "actual");
    Objects.requireNonNull(p10, "p10");
    Objects.requireNonNull(p50, "p50");
    Objects.requireNonNull(p90, "p90");
    Objects.requireNonNull(simpleP50, "simpleP50");
  }
}
