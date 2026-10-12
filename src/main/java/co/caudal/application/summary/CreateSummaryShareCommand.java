package co.caudal.application.summary;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Board authorizes an entity to see a summary (docs/API.md, section 13.4).
 *
 * @param caller the member of the Board who authorizes it
 * @param granteeUserId the user of the entity
 * @param scope MONTHLY_SUMMARY or MINUTES
 * @param periodFrom first day
 * @param periodTo last day, not before the first
 * @param reason why, 10 to 500 characters
 * @param expiresAt when it stops working; in the future
 */
public record CreateSummaryShareCommand(
    Caller caller,
    UUID granteeUserId,
    String scope,
    LocalDate periodFrom,
    LocalDate periodTo,
    String reason,
    Instant expiresAt)
    implements AuditableCommand<SummaryShareView> {

  @Override
  public String auditEntityType() {
    return "summary_share";
  }

  @Override
  public String auditEntityId(SummaryShareView result) {
    return result.id().toString();
  }
}
