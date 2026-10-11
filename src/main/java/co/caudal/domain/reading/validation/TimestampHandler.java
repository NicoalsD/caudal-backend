package co.caudal.domain.reading.validation;

import co.caudal.domain.reading.ReadingRules;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;

/**
 * Rejects a reading without its moment or with a moment in the future beyond the clock skew of the
 * phone (rules 2a and 2b, {@code MISSING_TIMESTAMP} and {@code FUTURE_TIMESTAMP}).
 *
 * @pattern P12 Chain of Responsibility
 */
public final class TimestampHandler extends ReadingValidationHandler {

  @Override
  protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
    if (candidate.observedAt() == null) {
      throw new DomainException(ErrorCode.MISSING_TIMESTAMP);
    }
    if (ReadingRules.isInFuture(candidate.observedAt(), candidate.now())) {
      throw new DomainException(ErrorCode.FUTURE_TIMESTAMP);
    }
  }
}
