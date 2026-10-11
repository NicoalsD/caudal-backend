package co.caudal.domain.reading.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.caudal.domain.reading.validation.ReadingCandidate.ReadingLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingValidationHandlerTest {

  static final Instant NOW = Instant.parse("2026-10-11T18:00:00Z");
  static final ReadingLimits LIMITS = new ReadingLimits(7, 30, new BigDecimal("0.50"));

  static ReadingCandidate candidate(String value, Instant observedAt) {
    return new ReadingCandidate(
        new BigDecimal(value),
        observedAt,
        NOW,
        BigDecimal.ZERO,
        new BigDecimal("5.00"),
        LIMITS,
        false);
  }

  static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void aValueOnTheGaugeWithTwoDecimalsPasses() {
    assertThat(ReadingValidationChain.run(new GaugeRangeHandler(), candidate("5.00", NOW)))
        .isEmpty();
    assertThat(ReadingValidationChain.run(new GaugeRangeHandler(), candidate("0", NOW))).isEmpty();
  }

  @Test
  void aValueOffTheGaugeOrWithThreeDecimalsIsRejectedWithItsLimits() {
    assertThatThrownBy(
            () -> ReadingValidationChain.run(new GaugeRangeHandler(), candidate("8.00", NOW)))
        .satisfies(
            failure -> {
              assertThat(codeOf(failure)).isEqualTo(ErrorCode.GAUGE_OUT_OF_RANGE);
              assertThat(((DomainException) failure).details())
                  .containsEntry("gauge_max", "5.00")
                  .containsEntry("value", "8.00");
            });
    assertThatThrownBy(
            () -> ReadingValidationChain.run(new GaugeRangeHandler(), candidate("2.105", NOW)))
        .extracting(ReadingValidationHandlerTest::codeOf)
        .isEqualTo(ErrorCode.GAUGE_OUT_OF_RANGE);
    assertThatThrownBy(
            () -> ReadingValidationChain.run(new GaugeRangeHandler(), candidate("-0.01", NOW)))
        .extracting(ReadingValidationHandlerTest::codeOf)
        .isEqualTo(ErrorCode.GAUGE_OUT_OF_RANGE);
  }

  @Test
  void theLinksRunInOrderAndARejectionStopsTheChain() {
    List<String> seen = new ArrayList<>();
    ReadingValidationHandler first = recorder("first", seen, false);
    first.linkWith(recorder("second", seen, true)).linkWith(recorder("third", seen, false));

    assertThatThrownBy(() -> ReadingValidationChain.run(first, candidate("1.00", NOW)))
        .isInstanceOf(DomainException.class);
    assertThat(seen).containsExactly("first", "second");
  }

  private static ReadingValidationHandler recorder(String name, List<String> seen, boolean reject) {
    return new ReadingValidationHandler() {
      @Override
      protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
        seen.add(name);
        if (reject) {
          throw new DomainException(ErrorCode.VALIDATION_ERROR);
        }
      }
    };
  }

  @Test
  void aMissingOrFutureMomentIsRejected() {
    ReadingValidationHandler chain = ReadingValidationChain.standard();

    assertThatThrownBy(() -> ReadingValidationChain.run(chain, candidate("1.00", null)))
        .extracting(ReadingValidationHandlerTest::codeOf)
        .isEqualTo(ErrorCode.MISSING_TIMESTAMP);
    assertThatThrownBy(
            () -> ReadingValidationChain.run(chain, candidate("1.00", NOW.plusSeconds(301))))
        .extracting(ReadingValidationHandlerTest::codeOf)
        .isEqualTo(ErrorCode.FUTURE_TIMESTAMP);
    assertThat(ReadingValidationChain.run(chain, candidate("1.00", NOW.plusSeconds(300))))
        .isEmpty();
  }

  @Test
  void anOldReadingIsRejectedUnlessItIsDemoHistory() {
    ReadingValidationHandler chain = ReadingValidationChain.standard();
    Instant eightDaysAgo = NOW.minusSeconds(8L * 24 * 3600);

    assertThatThrownBy(() -> ReadingValidationChain.run(chain, candidate("1.00", eightDaysAgo)))
        .satisfies(
            failure -> {
              assertThat(codeOf(failure)).isEqualTo(ErrorCode.TOO_OLD);
              assertThat(((DomainException) failure).details())
                  .containsEntry("max_backdate_days", 7);
            });
    ReadingCandidate history =
        new ReadingCandidate(
            BigDecimal.ONE,
            eightDaysAgo,
            NOW,
            BigDecimal.ZERO,
            new BigDecimal("5.00"),
            LIMITS,
            true);
    assertThat(ReadingValidationChain.run(chain, history)).isEmpty();
    assertThat(
            ReadingValidationChain.run(chain, candidate("1.00", NOW.minusSeconds(7L * 24 * 3600))))
        .isEmpty();
  }

  @Test
  void theRangeIsJudgedBeforeTheMoment() {
    assertThatThrownBy(
            () ->
                ReadingValidationChain.run(
                    ReadingValidationChain.standard(), candidate("9.00", null)))
        .extracting(ReadingValidationHandlerTest::codeOf)
        .isEqualTo(ErrorCode.GAUGE_OUT_OF_RANGE);
  }
}
