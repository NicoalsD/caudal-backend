package co.caudal.application.mfa;

import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.MfaRecoveryCodePort;
import co.caudal.application.port.out.SecretCipherPort;
import co.caudal.domain.mfa.Totp;
import co.caudal.domain.mfa.TotpSecret;
import co.caudal.domain.user.ResetCode;
import co.caudal.shared.FieldLimits;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Checks a second factor code of an account: a TOTP code of the app or, at login, one of the
 * recovery codes. A right TOTP code records its step and a right recovery code is marked as used,
 * so no code works twice. It joins the transaction of the caller.
 */
public class MfaVerifier {

  private static final Pattern TOTP_CODE = Pattern.compile(FieldLimits.TOTP_CODE_PATTERN);

  private final MfaFactorPort factors;
  private final MfaRecoveryCodePort recoveryCodes;
  private final SecretCipherPort cipher;

  /**
   * Creates the verifier.
   *
   * @param factors stored factors
   * @param recoveryCodes stored recovery codes
   * @param cipher decrypts the secret
   */
  public MfaVerifier(
      MfaFactorPort factors, MfaRecoveryCodePort recoveryCodes, SecretCipherPort cipher) {
    this.factors = factors;
    this.recoveryCodes = recoveryCodes;
    this.cipher = cipher;
  }

  /**
   * Checks the code typed at login: six digits are a TOTP code, anything else is tried as a
   * recovery code.
   *
   * @param factor the confirmed factor of the account
   * @param code the typed code
   * @param now the current moment
   * @return true if the code is right and was consumed
   */
  public boolean verifyLogin(MfaFactor factor, String code, Instant now) {
    if (code != null && TOTP_CODE.matcher(code).matches()) {
      return check(factor, code, now).isPresent();
    }
    return ResetCode.parse(code)
        .map(recovery -> recoveryCodes.consume(factor.userId(), recovery.hash(), now))
        .orElse(false);
  }

  /**
   * Checks a code and consumes it if it is right.
   *
   * @param factor the factor of the account
   * @param code the typed code
   * @param now the current moment
   * @return the step of the code, or empty if it is wrong or was already used
   */
  public OptionalLong check(MfaFactor factor, String code, Instant now) {
    TotpSecret secret = TotpSecret.of(cipher.decrypt(factor.secret(), factor.userId().toString()));
    OptionalLong step = Totp.verify(secret, code, now, factor.lastUsedStep());
    if (step.isPresent()
        && factor.confirmed()
        && !factors.markUsed(factor.id(), step.getAsLong())) {
      return OptionalLong.empty();
    }
    return step;
  }
}
