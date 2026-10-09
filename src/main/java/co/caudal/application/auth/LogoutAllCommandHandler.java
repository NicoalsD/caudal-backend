package co.caudal.application.auth;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.RevocationReason;

/**
 * Closes the sessions of the account on every device: all its refresh tokens are revoked with the
 * reason {@code LOGOUT_ALL} and {@code token_version} goes up, so the access token of the other
 * devices answers {@code UNAUTHORIZED} on its next call (HU-05). The token that sent the request
 * stops working too.
 *
 * @pattern P13 Command
 */
public class LogoutAllCommandHandler implements CommandHandler<LogoutAllCommand, Done> {

  private final SessionInvalidator sessions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param sessions ends the sessions of an account
   * @param unitOfWork transaction boundary
   */
  public LogoutAllCommandHandler(SessionInvalidator sessions, UnitOfWorkPort unitOfWork) {
    this.sessions = sessions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public Done handle(LogoutAllCommand command) {
    unitOfWork.execute(
        () -> sessions.invalidate(command.caller().userId(), RevocationReason.LOGOUT_ALL));
    return Done.DONE;
  }

  @Override
  public Class<LogoutAllCommand> commandType() {
    return LogoutAllCommand.class;
  }
}
