package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Corrects a reading without changing the original (docs/API.md, section 7.5; HU-13).
 *
 * @param caller who corrects it
 * @param readingId the reading
 * @param correctedGaugeValue the right value
 * @param reason why, 10 to 500 characters
 */
public record CorrectReadingCommand(
    Caller caller, UUID readingId, BigDecimal correctedGaugeValue, String reason)
    implements AuditableCommand<CorrectionResult> {

  @Override
  public String auditEntityType() {
    return "reading_correction";
  }

  @Override
  public String auditEntityId(CorrectionResult result) {
    return result.correction().id().toString();
  }
}
