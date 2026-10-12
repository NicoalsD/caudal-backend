package co.caudal.application.mfa;

import co.caudal.application.auth.LoginAccount;
import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.SecretCipherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.mfa.TotpSecret;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/**
 * Creates an unconfirmed TOTP factor and returns its secret once (docs/Seguridad.md, section 2.4;
 * HU-06). Only the roles of {@link MfaSettings#allowedRoles()} may turn MFA on. An unconfirmed
 * factor is replaced; a confirmed one is a conflict, because replacing it would let anyone holding
 * a stolen session remove the second factor.
 *
 * @pattern P13 Command
 */
public class EnrollMfaCommandHandler implements CommandHandler<EnrollMfaCommand, MfaEnrollment> {

  private final MfaFactorPort factors;
  private final SecretCipherPort cipher;
  private final UserAccountPort accounts;
  private final MfaSettings settings;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param factors stored factors
   * @param cipher encrypts the secret
   * @param accounts accounts, for the name shown in the app
   * @param settings issuer and allowed roles
   * @param unitOfWork transaction boundary
   */
  public EnrollMfaCommandHandler(
      MfaFactorPort factors,
      SecretCipherPort cipher,
      UserAccountPort accounts,
      MfaSettings settings,
      UnitOfWorkPort unitOfWork) {
    this.factors = factors;
    this.cipher = cipher;
    this.accounts = accounts;
    this.settings = settings;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public MfaEnrollment handle(EnrollMfaCommand command) {
    if (!settings.allowedRoles().contains(command.caller().role())) {
      throw new DomainException(ErrorCode.FORBIDDEN);
    }
    return unitOfWork.executeAs(command.caller().scope(), () -> enroll(command));
  }

  private MfaEnrollment enroll(EnrollMfaCommand command) {
    var userId = command.caller().userId();
    LoginAccount account =
        accounts.findById(userId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (factors.find(userId).map(MfaFactor::confirmed).orElse(false)) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "MFA_ALREADY_ACTIVE"));
    }
    factors.deleteUnconfirmed(userId);
    TotpSecret secret = TotpSecret.generate();
    factors.insert(userId, cipher.encrypt(secret.bytes(), userId.toString()));
    return new MfaEnrollment(
        secret.base32(), secret.otpauthUri(settings.issuer(), account.username()));
  }

  @Override
  public Class<EnrollMfaCommand> commandType() {
    return EnrollMfaCommand.class;
  }
}
