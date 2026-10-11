package co.caudal.application.mfa;

import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.SecretCipherPort;
import co.caudal.domain.mfa.Totp;
import co.caudal.domain.mfa.TotpSecret;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Checks a TOTP code against the stored factor of an account and, when it is right, records its
 * step so the same code never works again. It joins the transaction of the caller.
 */
public class MfaVerifier {

  private final MfaFactorPort factors;
  private final SecretCipherPort cipher;

  /**
   * Creates the verifier.
   *
   * @param factors stored factors
   * @param cipher decrypts the secret
   */
  public MfaVerifier(MfaFactorPort factors, SecretCipherPort cipher) {
    this.factors = factors;
    this.cipher = cipher;
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
