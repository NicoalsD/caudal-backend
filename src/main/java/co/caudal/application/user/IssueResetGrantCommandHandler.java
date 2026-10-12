package co.caudal.application.user;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAdminPort;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.domain.user.AccountState;
import co.caudal.domain.user.ResetCode;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Issues a one-time reset code for an account of the aqueduct (docs/API.md, section 3.4). The codes
 * issued before and not used stop working, so only the newest one is valid. Only the SHA-256 of the
 * code is stored; the code is returned once.
 *
 * @pattern P13 Command
 */
public class IssueResetGrantCommandHandler
    implements CommandHandler<IssueResetGrantCommand, IssuedResetGrant> {

  private final UserAdminPort users;
  private final PasswordResetGrantPort grants;
  private final ResetGrantSettings settings;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param users accounts of the aqueduct
   * @param grants one-time codes
   * @param settings lifetime of a code
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public IssueResetGrantCommandHandler(
      UserAdminPort users,
      PasswordResetGrantPort grants,
      ResetGrantSettings settings,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.users = users;
    this.grants = grants;
    this.settings = settings;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public IssuedResetGrant handle(IssueResetGrantCommand command) {
    ChangeReason.require(command.reason());
    ResetCode code = ResetCode.generate();
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ManagedUser user =
              users
                  .findInAqueduct(command.caller().aqueductId(), command.userId())
                  .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          if (AccountState.DISABLED.equals(user.status())) {
            throw new DomainException(
                ErrorCode.INVALID_STATE_TRANSITION, Map.of("status", user.status()));
          }
          Instant now = clock.instant();
          grants.revokeOpen(user.id(), now);
          Instant expiresAt = now.plus(settings.timeToLive());
          UUID id = grants.save(user.id(), code.hash(), command.caller().userId(), expiresAt);
          return new IssuedResetGrant(id, user.id(), code.display(), expiresAt);
        });
  }

  @Override
  public Class<IssueResetGrantCommand> commandType() {
    return IssueResetGrantCommand.class;
  }
}
