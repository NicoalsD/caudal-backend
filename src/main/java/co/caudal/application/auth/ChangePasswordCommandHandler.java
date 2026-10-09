package co.caudal.application.auth;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.command.Done;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.NormalizedPassword;

/**
 * Changes the password of the person who is signed in (docs/API.md, section 2.5; HU-02).
 *
 * <p>The current password must be right (otherwise {@code UNAUTHORIZED}); the new one must meet
 * {@link PasswordRenewal}. On success the flag {@code must_change_password} is cleared, {@code
 * token_version} goes up and every refresh token is revoked with {@code PASSWORD_CHANGED}, so the
 * person signs in again with the new password.
 *
 * @pattern P13 Command
 */
public class ChangePasswordCommandHandler implements CommandHandler<ChangePasswordCommand, Done> {

  private final UserAccountPort accounts;
  private final PasswordHasherPort passwords;
  private final PasswordRenewal renewal;
  private final SessionInvalidator sessions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param accounts accounts
   * @param passwords hashing of passwords
   * @param renewal rules and storage of a new password
   * @param sessions ends the sessions of an account
   * @param unitOfWork transaction boundary
   */
  public ChangePasswordCommandHandler(
      UserAccountPort accounts,
      PasswordHasherPort passwords,
      PasswordRenewal renewal,
      SessionInvalidator sessions,
      UnitOfWorkPort unitOfWork) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.renewal = renewal;
    this.sessions = sessions;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public Done handle(ChangePasswordCommand command) {
    NormalizedPassword current = NormalizedPassword.of(command.currentPassword());
    NormalizedPassword next = NormalizedPassword.of(command.newPassword());
    unitOfWork.execute(
        () -> {
          LoginAccount account =
              accounts.findById(command.caller().userId()).orElseThrow(UnauthorizedException::new);
          if (LoginAccount.DISABLED.equals(account.status())
              || !passwords.matches(current, account.passwordHash())) {
            throw new UnauthorizedException();
          }
          renewal.renew(account, next, false);
          return sessions.invalidate(account.id(), RevocationReason.PASSWORD_CHANGED);
        });
    return Done.DONE;
  }

  @Override
  public Class<ChangePasswordCommand> commandType() {
    return ChangePasswordCommand.class;
  }
}
