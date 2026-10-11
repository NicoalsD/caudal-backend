package co.caudal.application.user;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Grants a role in the administrator's aqueduct to an existing account (docs/API.md, section 3.6;
 * HU-04). An account holds one role at a time in an aqueduct, so a validity that overlaps another
 * membership of the same aqueduct is a conflict.
 *
 * @pattern P13 Command
 */
public class GrantMembershipCommandHandler
    implements CommandHandler<GrantMembershipCommand, MembershipView> {

  private final MembershipAdminPort memberships;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param memberships memberships
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public GrantMembershipCommandHandler(
      MembershipAdminPort memberships, UnitOfWorkPort unitOfWork, Clock clock) {
    this.memberships = memberships;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public MembershipView handle(GrantMembershipCommand command) {
    if (!command.aqueductId().equals(command.caller().aqueductId())) {
      throw new DomainException(ErrorCode.FORBIDDEN);
    }
    Instant from = command.validFrom() == null ? clock.instant() : command.validFrom();
    if (command.validTo() != null && !command.validTo().isAfter(from)) {
      throw new FieldValidationException("valid_to", "FIELD_OUT_OF_RANGE");
    }
    return unitOfWork.executeAs(command.caller().scope(), () -> grant(command, from));
  }

  private MembershipView grant(GrantMembershipCommand command, Instant from) {
    if (!memberships.roleExists(command.roleCode())) {
      throw new FieldValidationException("role_code", "FIELD_VALUE_NOT_ALLOWED");
    }
    if (!memberships.userExists(command.userId())) {
      throw new DomainException(ErrorCode.NOT_FOUND);
    }
    if (memberships.overlaps(command.userId(), command.aqueductId(), from, command.validTo())) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "MEMBERSHIP_OVERLAP"));
    }
    UUID grantedBy = command.caller().userId();
    UUID id =
        memberships.insert(
            command.userId(),
            command.aqueductId(),
            command.roleCode(),
            from,
            command.validTo(),
            grantedBy);
    return new MembershipView(
        id,
        command.userId(),
        command.aqueductId(),
        command.roleCode(),
        from,
        command.validTo(),
        grantedBy);
  }

  @Override
  public Class<GrantMembershipCommand> commandType() {
    return GrantMembershipCommand.class;
  }
}
