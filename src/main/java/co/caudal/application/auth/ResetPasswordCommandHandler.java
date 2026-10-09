package co.caudal.application.auth;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.session.RevocationReason;
import co.caudal.domain.session.UnauthorizedException;
import co.caudal.domain.user.Membership;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.domain.user.ResetCode;
import co.caudal.domain.user.Username;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sets a password with the one-time code that the Board issued (docs/API.md, section 2.6; HU-02).
 * It serves both the activation of a new account and a forgotten password.
 *
 * <p>Every way to fail the code (unknown account, disabled account, malformed, unknown, used,
 * revoked or expired code) answers {@code UNAUTHORIZED} with the same body, and the paths that
 * never reach the password hashing spend a decoy verification so the time does not tell them apart.
 * The code is checked first, then the password policy, and only then is the code consumed with a
 * conditional update, so a weak password (422) does not burn the code and two concurrent uses
 * cannot both succeed. A reset does not change {@code must_change_password}: a new account keeps it
 * until the person changes the password after the first sign in. It ends every session of the
 * account.
 *
 * @pattern P13 Command
 */
public class ResetPasswordCommandHandler
    implements CommandHandler<ResetPasswordCommand, PasswordResetResult> {

  private final LoginAccountPort accounts;
  private final PasswordResetGrantPort grants;
  private final MembershipPort memberships;
  private final PasswordHasherPort passwords;
  private final PasswordRenewal renewal;
  private final SessionInvalidator sessions;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param ports the ports it needs, grouped to keep the constructor small
   * @param renewal rules and storage of a new password
   * @param sessions ends the sessions of an account
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ResetPasswordCommandHandler(
      ResetPasswordPorts ports,
      PasswordRenewal renewal,
      SessionInvalidator sessions,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.accounts = ports.accounts();
    this.grants = ports.grants();
    this.memberships = ports.memberships();
    this.passwords = ports.passwords();
    this.renewal = renewal;
    this.sessions = sessions;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public PasswordResetResult handle(ResetPasswordCommand command) {
    NormalizedPassword password = NormalizedPassword.of(command.newPassword());
    Optional<Username> username = Username.tryParse(command.username());
    Optional<ResetCode> code = ResetCode.parse(command.code());
    if (username.isEmpty() || code.isEmpty()) {
      return refuse(password);
    }
    Instant now = clock.instant();
    return unitOfWork.execute(() -> reset(username.get(), code.get(), password, now));
  }

  private PasswordResetResult reset(
      Username username, ResetCode code, NormalizedPassword password, Instant now) {
    Optional<LoginAccount> found =
        accounts
            .findByUsername(username.value())
            .filter(account -> !LoginAccount.DISABLED.equals(account.status()));
    Optional<UUID> grant =
        found.flatMap(account -> grants.findUsable(account.id(), code.hash(), now));
    if (grant.isEmpty()) {
      return refuse(password);
    }
    LoginAccount account = found.get();
    renewal.renew(account, password, account.mustChangePassword());
    if (!grants.markUsed(grant.get(), now)) {
      throw new UnauthorizedException();
    }
    sessions.invalidate(account.id(), RevocationReason.PASSWORD_CHANGED);
    return new PasswordResetResult(account.id(), aqueductOf(account.id(), now));
  }

  private UUID aqueductOf(UUID userId, Instant now) {
    List<Membership> active =
        unitOfWork.executeAs(
            SessionScope.ofUser(userId), () -> memberships.findActive(userId, now));
    return active.isEmpty() ? null : active.getFirst().aqueductId();
  }

  private PasswordResetResult refuse(NormalizedPassword password) {
    passwords.verifyAgainstDecoy(password);
    throw new UnauthorizedException();
  }

  @Override
  public Class<ResetPasswordCommand> commandType() {
    return ResetPasswordCommand.class;
  }
}
