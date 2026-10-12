package co.caudal.domain.reading.validation;

import co.caudal.domain.reading.ReadingRules;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;

/**
 * Rejects a reading older than {@code max_backdate_days} of the rules (rule 5, {@code TOO_OLD}),
 * except simulated history imported into a demo aqueduct.
 *
 * @pattern P12 Chain of Responsibility
 */
public final class AgeHandler extends ReadingValidationHandler {

  @Override
  protected void check(ReadingCandidate candidate, List<ReadingIssue> issues) {
    int days = candidate.limits().maxBackdateDays();
    if (!candidate.ageExempt()
        && ReadingRules.isTooOld(candidate.observedAt(), candidate.now(), days)) {
      throw new DomainException(ErrorCode.TOO_OLD, Map.of("max_backdate_days", days));
    }
  }
}
