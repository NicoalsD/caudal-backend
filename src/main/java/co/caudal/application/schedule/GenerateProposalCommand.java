package co.caudal.application.schedule;

import co.caudal.application.auth.Caller;
import co.caudal.application.command.AuditableCommand;
import java.time.LocalDate;

/**
 * Someone asks for the shifts of a day (docs/API.md, section 10.1; HU-08).
 *
 * @param caller who asks
 * @param serviceDate the day of service
 */
public record GenerateProposalCommand(Caller caller, LocalDate serviceDate)
    implements AuditableCommand<ProposalDetail> {

  @Override
  public String auditEntityType() {
    return "schedule_proposal";
  }

  @Override
  public String auditEntityId(ProposalDetail result) {
    return result.summary().id().toString();
  }
}
