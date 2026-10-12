package co.caudal.application.user;

import co.caudal.application.auth.SessionInvalidator;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.user.AccountState;
import co.caudal.domain.user.PersonName;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Changes the name or the status of an account (docs/API.md, section 3.3; HU-04).
 *
 * <p>The allowed status changes come from {@link AccountState}. Locking or disabling ends every
 * session of the account in the same transaction (token version and refresh tokens), and unlocking
 * clears the failed logins. An administrator cannot lock or disable their own account, so a Board
 * never locks itself out by mistake.
 *
 * @pattern P13 Command
 */
public class UpdateUserCommandHandler implements CommandHandler<UpdateUserCommand, ManagedUser> {

  private final UserAdminPort users;
  private final SessionInvalidator sessions;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param users accounts of the aqueduct
   * @param sessions ends the sessions of a locked or disabled account
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public UpdateUserCommandHandler(
      UserAdminPort users, SessionInvalidator sessions, UnitOfWorkPort unitOfWork, Clock clock) {
    this.users = users;
    this.sessions = sessions;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public ManagedUser handle(UpdateUserCommand command) {
    PersonName name = command.fullName() == null ? null : PersonName.parse(command.fullName());
    return unitOfWork.executeAs(command.caller().scope(), () -> update(command, name));
  }

  private ManagedUser update(UpdateUserCommand command, PersonName name) {
    ManagedUser current =
        users
            .findInAqueduct(command.caller().aqueductId(), command.userId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    AccountState before = AccountState.of(current.status(), current.lockedUntil());
    AccountState after = command.status() == null ? before : before.changeTo(command.status());
    boolean statusChanged = after != before;
    if (statusChanged
        && after.endsSessions()
        && command.userId().equals(command.caller().userId())) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "SELF_LOCKOUT"));
    }
    Instant now = clock.instant();
    String fullName = name == null ? current.fullName() : name.value();
    Instant disabledAt = AccountState.DISABLED.equals(after.code()) ? now : null;
    boolean unlock =
        AccountState.LOCKED.equals(before.code()) && AccountState.ACTIVE.equals(after.code());
    users.saveAdministration(
        current.id(), fullName, after.code(), after.lockedUntil(), disabledAt, unlock);
    if (statusChanged && after.endsSessions()) {
      sessions.invalidate(current.id(), RevocationReason.ADMIN);
    }
    return new ManagedUser(
        current.id(),
        current.username(),
        fullName,
        after.code(),
        after.lockedUntil(),
        current.mustChangePassword(),
        current.lastLoginAt());
  }

  @Override
  public Class<UpdateUserCommand> commandType() {
    return UpdateUserCommand.class;
  }
}
