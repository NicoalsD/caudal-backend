package co.caudal.application.user;

import co.caudal.application.auth.SessionScope;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.user.PersonName;
import co.caudal.domain.user.ResetCode;
import co.caudal.domain.user.UnusablePassword;
import co.caudal.domain.user.Username;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates an account without a known password (docs/API.md, section 3.2; HU-04).
 *
 * <p>The account is stored with the Argon2id hash of 32 random bytes that nobody knows, with {@code
 * must_change_password} on, and the system issues a one-time activation code in {@code
 * iam.password_reset_grants}. The code is returned once; only its SHA-256 is stored. The person
 * sets a password with {@code POST /auth/reset-password}.
 *
 * <p>The aqueduct of the new membership must be the aqueduct of the administrator's session: the
 * database only accepts memberships of the session's aqueduct, and a request must never reach
 * another aqueduct than the one the token proves.
 *
 * @pattern P13 Command
 */
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, CreatedUser> {

  /** Status of a new account. */
  static final String STATUS_ACTIVE = "ACTIVE";

  private final UserAccountPort accounts;
  private final MembershipAdminPort memberships;
  private final PasswordResetGrantPort grants;
  private final PasswordHasherPort passwords;
  private final ResetGrantSettings settings;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param accounts accounts
   * @param memberships memberships
   * @param grants one-time codes
   * @param passwords hashing of passwords
   * @param settings lifetime of the activation code
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public CreateUserCommandHandler(
      UserAccountPort accounts,
      MembershipAdminPort memberships,
      PasswordResetGrantPort grants,
      PasswordHasherPort passwords,
      ResetGrantSettings settings,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.accounts = accounts;
    this.memberships = memberships;
    this.grants = grants;
    this.passwords = passwords;
    this.settings = settings;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public CreatedUser handle(CreateUserCommand command) {
    if (!command.aqueductId().equals(command.caller().aqueductId())) {
      throw new DomainException(ErrorCode.FORBIDDEN);
    }
    Username username = Username.parse(command.username());
    PersonName fullName = PersonName.parse(command.fullName());
    // Hashing costs time and needs no transaction: it is done before one is opened.
    String unusableHash = passwords.hash(UnusablePassword.generate());
    Instant now = clock.instant();
    ResetCode code = ResetCode.generate();
    SessionScope scope = new SessionScope(command.caller().userId(), command.caller().aqueductId());
    return unitOfWork.executeAs(
        scope, () -> create(command, username, fullName, unusableHash, code, now));
  }

  private CreatedUser create(
      CreateUserCommand command,
      Username username,
      PersonName fullName,
      String unusableHash,
      ResetCode code,
      Instant now) {
    UUID administrator = command.caller().userId();
    if (!memberships.roleExists(command.role())) {
      throw new FieldValidationException("role", "FIELD_VALUE_NOT_ALLOWED");
    }
    UUID userId =
        accounts
            .insertIfAbsent(username.value(), fullName.value(), unusableHash, administrator)
            .orElseThrow(() -> new DomainException(ErrorCode.CONFLICT));
    UUID membershipId =
        memberships.insert(userId, command.aqueductId(), command.role(), now, null, administrator);
    Instant expiresAt = now.plus(settings.timeToLive());
    grants.save(userId, code.hash(), administrator, expiresAt);
    return new CreatedUser(
        userId,
        username.value(),
        fullName.value(),
        STATUS_ACTIVE,
        true,
        membershipId,
        command.aqueductId(),
        command.role(),
        code.display(),
        expiresAt);
  }

  @Override
  public Class<CreateUserCommand> commandType() {
    return CreateUserCommand.class;
  }
}
