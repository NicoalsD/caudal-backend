package co.caudal.domain.reading.validation;

import co.caudal.domain.reading.ReadingRules;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;

/**
 * Rejects a value outside the painted gauge of the tank or with more than two decimals (rule 1,
 * {@code GAUGE_OUT_OF_RANGE}).
 *
 * @pattern P12 Chain of Responsibility
 */
public final class GaugeRangeHandler extends ReadingValidationHandler {

  @Override
  protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
    if (!ReadingRules.isWithinGauge(
        candidate.value(), candidate.gaugeMin(), candidate.gaugeMax())) {
      throw new DomainException(
          ErrorCode.GAUGE_OUT_OF_RANGE,
          Map.of(
              "gauge_min", candidate.gaugeMin().toPlainString(),
              "gauge_max", candidate.gaugeMax().toPlainString(),
              "value", candidate.value().toPlainString()));
    }
  }
}
