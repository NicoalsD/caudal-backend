package co.caudal.infrastructure.config;

import co.caudal.application.mfa.ConfirmMfaCommandHandler;
import co.caudal.application.mfa.EnrollMfaCommandHandler;
import co.caudal.application.mfa.MfaSettings;
import co.caudal.application.mfa.MfaVerifier;
import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.MfaRecoveryCodePort;
import co.caudal.application.port.out.SecretCipherPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.infrastructure.security.MfaProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use cases of the second factor (TOTP). */
@Configuration(proxyBeanMethods = false)
class MfaServicesConfig {

  @Bean
  MfaSettings mfaSettings(MfaProperties properties) {
    return new MfaSettings(properties.issuer(), properties.allowedRoles());
  }

  @Bean
  MfaVerifier mfaVerifier(
      MfaFactorPort factors, MfaRecoveryCodePort recoveryCodes, SecretCipherPort cipher) {
    return new MfaVerifier(factors, recoveryCodes, cipher);
  }

  @Bean
  EnrollMfaCommandHandler enrollMfaCommandHandler(
      MfaFactorPort factors,
      SecretCipherPort cipher,
      UserAccountPort accounts,
      MfaSettings settings,
      UnitOfWorkPort unitOfWork) {
    return new EnrollMfaCommandHandler(factors, cipher, accounts, settings, unitOfWork);
  }

  @Bean
  ConfirmMfaCommandHandler confirmMfaCommandHandler(
      MfaFactorPort factors,
      MfaRecoveryCodePort recoveryCodes,
      MfaVerifier verifier,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ConfirmMfaCommandHandler(factors, recoveryCodes, verifier, unitOfWork, clock);
  }
}
