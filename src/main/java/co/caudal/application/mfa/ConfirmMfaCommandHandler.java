package co.caudal.application.mfa;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.MfaRecoveryCodePort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.user.ResetCode;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import co.caudal.shared.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Confirms a TOTP factor with a first code (HU-06) and issues {@link
 * FieldLimits#MFA_RECOVERY_CODES} one-time recovery codes, shown once; only their SHA-256 is
 * stored. From then on the login asks for a code.
 *
 * @pattern P13 Command
 */
public class ConfirmMfaCommandHandler
    implements CommandHandler<ConfirmMfaCommand, MfaConfirmation> {

  private final MfaFactorPort factors;
  private final MfaRecoveryCodePort recoveryCodes;
  private final MfaVerifier verifier;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param factors stored factors
   * @param recoveryCodes stored recovery codes
   * @param verifier checks the code
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ConfirmMfaCommandHandler(
      MfaFactorPort factors,
      MfaRecoveryCodePort recoveryCodes,
      MfaVerifier verifier,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    this.factors = factors;
    this.recoveryCodes = recoveryCodes;
    this.verifier = verifier;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public MfaConfirmation handle(ConfirmMfaCommand command) {
    return unitOfWork.executeAs(command.caller().scope(), () -> confirm(command));
  }

  private MfaConfirmation confirm(ConfirmMfaCommand command) {
    MfaFactor factor =
        factors
            .find(command.caller().userId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    if (factor.confirmed()) {
      throw new DomainException(ErrorCode.CONFLICT, Map.of("reason", "MFA_ALREADY_ACTIVE"));
    }
    Instant now = clock.instant();
    OptionalLong step = verifier.check(factor, command.code(), now);
    if (step.isEmpty()) {
      throw new FieldValidationException("code", "FIELD_VALUE_NOT_ALLOWED");
    }
    factors.confirm(factor.id(), now, step.getAsLong());
    List<ResetCode> codes =
        Stream.generate(ResetCode::generate).limit(FieldLimits.MFA_RECOVERY_CODES).toList();
    recoveryCodes.insertAll(factor.userId(), codes.stream().map(ResetCode::hash).toList());
    return new MfaConfirmation(codes.stream().map(ResetCode::display).toList());
  }

  @Override
  public Class<ConfirmMfaCommand> commandType() {
    return ConfirmMfaCommand.class;
  }
}
