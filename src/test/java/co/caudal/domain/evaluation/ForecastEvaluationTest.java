package co.caudal.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The verdict of the evaluation: sample size, calibrated coverage and positive skill. */
class ForecastEvaluationTest {

  static EvaluationSample day(
      int offset, String actual, String p10, String p50, String p90, String simple) {
    return new EvaluationSample(
        LocalDate.of(2026, 10, 1).plusDays(offset),
        new BigDecimal(actual),
        new BigDecimal(p10),
        new BigDecimal(p50),
        new BigDecimal(p90),
        new BigDecimal(simple));
  }

  /** A day inside the interval where the AI is much closer than the simple estimate. */
  static EvaluationSample covered(int offset) {
    return day(offset, "2.00", "1.80", "2.10", "2.40", "2.60");
  }

  /** A day outside the interval where the AI and the simple estimate are both off. */
  static EvaluationSample missed(int offset) {
    return day(offset, "4.00", "1.80", "2.10", "2.40", "1.00");
  }

  /** Thirty days, 80 % of them covered by the interval. */
  static List<EvaluationSample> thirtyWithEightyPercentCoverage() {
    List<EvaluationSample> samples = new ArrayList<>();
    for (int i = 0; i < 24; i++) {
      samples.add(covered(i));
    }
    for (int i = 24; i < 30; i++) {
      samples.add(missed(i));
    }
    return samples;
  }

  @Test
  void aVerdictNeedsThirtyForecasts() {
    List<EvaluationSample> twentyNine = new ArrayList<>();
    for (int i = 0; i < 29; i++) {
      twentyNine.add(covered(i));
    }

    ForecastEvaluation.Report report = ForecastEvaluation.evaluate(1, twentyNine);

    assertThat(report.n()).isEqualTo(29);
    assertThat(report.helps()).isEmpty();
  }

  @Test
  void theAiHelpsWithSkillAndCalibratedCoverage() {
    ForecastEvaluation.Report report =
        ForecastEvaluation.evaluate(1, thirtyWithEightyPercentCoverage());

    assertThat(report.n()).isEqualTo(30);
    assertThat(report.metrics().get("coverage_80")).isEqualByComparingTo("0.8");
    assertThat(report.metrics().get("skill")).isPositive();
    assertThat(report.helps()).hasValue(true);
  }

  @Test
  void aCoverageOutsideTheCalibrationRangeDoesNotHelp() {
    List<EvaluationSample> allCovered = new ArrayList<>();
    List<EvaluationSample> noneCovered = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      allCovered.add(covered(i));
      noneCovered.add(missed(i));
    }

    ForecastEvaluation.Report high = ForecastEvaluation.evaluate(1, allCovered);
    ForecastEvaluation.Report low = ForecastEvaluation.evaluate(1, noneCovered);

    assertThat(high.metrics().get("coverage_80")).isEqualByComparingTo("1");
    assertThat(high.metrics().get("skill")).isPositive();
    assertThat(high.helps()).hasValue(false);
    assertThat(low.metrics().get("coverage_80")).isEqualByComparingTo("0");
    assertThat(low.helps()).hasValue(false);
  }

  @Test
  void aSimpleEstimateWithoutErrorLeavesSkillOutAndDoesNotHelp() {
    List<EvaluationSample> samples = new ArrayList<>();
    for (int i = 0; i < 24; i++) {
      samples.add(day(i, "2.00", "1.80", "2.10", "2.40", "2.00"));
    }
    for (int i = 24; i < 30; i++) {
      samples.add(day(i, "4.00", "1.80", "2.10", "2.40", "4.00"));
    }

    ForecastEvaluation.Report report = ForecastEvaluation.evaluate(1, samples);

    assertThat(report.metrics().get("mae_simple")).isEqualByComparingTo("0");
    assertThat(report.metrics()).doesNotContainKey("skill");
    assertThat(report.helps()).hasValue(false);
  }
}
