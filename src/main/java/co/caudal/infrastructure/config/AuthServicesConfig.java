package co.caudal.infrastructure.config;

import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.application.auth.LoginPorts;
import co.caudal.application.auth.LoginSettings;
import co.caudal.application.auth.LoginUseCase;
import co.caudal.application.auth.RefreshSessionUseCase;
import co.caudal.application.auth.RefreshTokenService;
import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.LoginAttemptPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PermissionPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserProfilePort;
import co.caudal.domain.user.LockoutPolicy;
import co.caudal.infrastructure.security.LoginProperties;
import co.caudal.infrastructure.security.RefreshProperties;
import co.caudal.shared.PhysicalConstants;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases and services of the {@code application} layer, which has no Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshProperties.class)
class AuthServicesConfig {

  @Bean
  RefreshTokenService refreshTokenService(
      RefreshTokenPort tokens, SecurityEventPort events, UnitOfWorkPort unitOfWork, Clock clock) {
    return new RefreshTokenService(tokens, events, unitOfWork, clock);
  }

  @Bean
  GetCurrentUserUseCase getCurrentUserUseCase(
      UserProfilePort profiles,
      MembershipPort memberships,
      PermissionPort permissions,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new GetCurrentUserUseCase(profiles, memberships, permissions, unitOfWork, clock);
  }

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  LoginUseCase loginUseCase(
      LoginAccountPort accounts,
      LoginAttemptPort attempts,
      MembershipPort memberships,
      PasswordHasherPort passwords,
      PrivacyHasherPort hasher,
      AccessTokenIssuerPort accessTokens,
      RefreshTokenService refreshTokens,
      SecurityEventPort events,
      UnitOfWorkPort unitOfWork,
      LoginProperties login,
      RefreshProperties refresh,
      Clock clock) {
    Duration lock = Duration.ofMinutes(login.lockMinutes());
    LockoutPolicy policy =
        new LockoutPolicy(
            login.maxFailures(), lock, Duration.ofHours(PhysicalConstants.HOURS_PER_DAY));
    LoginSettings settings =
        new LoginSettings(
            lock,
            login.ipMaxAttempts(),
            Duration.ofMinutes(login.ipWindowMinutes()),
            refresh::ttlFor);
    return new LoginUseCase(
        new LoginPorts(
            accounts,
            attempts,
            memberships,
            passwords,
            hasher,
            accessTokens,
            refreshTokens,
            events,
            unitOfWork),
        policy,
        settings,
        clock);
  }

  @Bean
  RefreshSessionUseCase refreshSessionUseCase(
      RefreshTokenService refreshTokens,
      TokenVersionPort versions,
      MembershipPort memberships,
      AccessTokenIssuerPort accessTokens,
      PrivacyHasherPort hasher,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new RefreshSessionUseCase(
        refreshTokens, versions, memberships, accessTokens, hasher, unitOfWork, clock);
  }
}
