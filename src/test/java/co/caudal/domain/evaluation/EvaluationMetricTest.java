package co.caudal.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class EvaluationMetricTest {

  static EvaluationSample sample(String actual, String p10, String p50, String p90, String simple) {
    return new EvaluationSample(
        LocalDate.of(2026, 10, 2),
        new BigDecimal(actual),
        new BigDecimal(p10),
        new BigDecimal(p50),
        new BigDecimal(p90),
        new BigDecimal(simple));
  }

  static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

  static final List<EvaluationSample> TWO =
      List.of(
          sample("2.00", "1.80", "2.10", "2.40", "2.40"),
          sample("3.00", "2.50", "2.80", "2.90", "2.60"));

  @Test
  void maeIsTheMeanAbsoluteErrorOfEachForecast() {
    assertThat(MeanAbsoluteError.ofAi().compute(TWO))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.15"));
    assertThat(MeanAbsoluteError.ofSimple().compute(TWO))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.40"));
  }

  @Test
  void skillComparesWithTheSimpleEstimate() {
    assertThat(new Skill().compute(TWO))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.625"));
  }

  @Test
  void coverageAndWidthDescribeTheRange() {
    assertThat(new IntervalCoverage().compute(TWO))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.5"));
    assertThat(new IntervalWidth().compute(TWO))
        .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.5"));
  }

  @Test
  void wqlWeighsEveryQuantile() {
    // y=2: p10 1.8 -> 0.1*0.2=0.02; p50 2.1 -> 0.5*0.1=0.05; p90 2.4 -> 0.1*0.4=0.04
    // y=3: p10 2.5 -> 0.1*0.5=0.05; p50 2.8 -> 0.5*0.2=0.10; p90 2.9 -> 0.9*0.1=0.09
    // sum |y| = 5; WQL = (2*0.07/5 + 2*0.15/5 + 2*0.13/5) / 3 = 0.14 / 3
    assertThat(new WeightedQuantileLoss().compute(TWO))
        .hasValueSatisfying(
            v -> assertThat(v).isCloseTo(new BigDecimal("0.046667"), Offset.offset(TOLERANCE)));
    assertThat(WeightedQuantileLoss.loss(new BigDecimal("0.9"), BigDecimal.ONE, BigDecimal.TEN))
        .isEqualByComparingTo("0.9");
  }

  @Test
  void noSamplesMeansNoValue() {
    for (EvaluationMetric metric :
        List.<EvaluationMetric>of(
            MeanAbsoluteError.ofAi(),
            new WeightedQuantileLoss(),
            new IntervalCoverage(),
            new IntervalWidth(),
            new Skill())) {
      assertThat(metric.compute(List.of())).isEmpty();
    }
  }
}
