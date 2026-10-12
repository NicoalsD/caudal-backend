package co.caudal.application.user;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Ends a membership (docs/API.md, section 3.7; HU-04). The row is never deleted, because the audit
 * refers to it: {@code valid_to} becomes now. The token version of the account goes up, so the
 * access tokens issued with the old role stop working at their next use. Ending a membership that
 * already ended changes nothing. An administrator cannot end their own current membership.
 *
 * @pattern P13 Command
 */
public class RevokeMembershipCommandHandler
    implements CommandHandler<RevokeMembershipCommand, Done> {

  private final MembershipAdminPort memberships;
  private final UserAccountPort accounts;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param memberships memberships
   * @param accounts accounts, to raise the token version
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public RevokeMembershipCommandHandler(
      MembershipAdminPort memberships,
      UserAccountPort accounts,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.memberships = memberships;
    this.accounts = accounts;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public Done handle(RevokeMembershipCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> revoke(command));
  }

  private Done revoke(RevokeMembershipCommand command) {
    MembershipView membership =
        memberships
            .find(command.caller().aqueductId(), command.membershipId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    Instant now = clock.instant();
    if (membership.validTo() != null && !membership.validTo().isAfter(now)) {
      return Done.DONE;
    }
    if (membership.userId().equals(command.caller().userId())) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "SELF_LOCKOUT"));
    }
    memberships.close(membership.id(), now);
    accounts.incrementTokenVersion(membership.userId());
    return Done.DONE;
  }

  @Override
  public Class<RevokeMembershipCommand> commandType() {
    return RevokeMembershipCommand.class;
  }
}
