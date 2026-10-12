package co.caudal.infrastructure.config;

import co.caudal.application.auth.ChangePasswordCommandHandler;
import co.caudal.application.auth.GetCurrentUserUseCase;
import co.caudal.application.auth.LoginPorts;
import co.caudal.application.auth.LoginSettings;
import co.caudal.application.auth.LoginUseCase;
import co.caudal.application.auth.LogoutAllCommandHandler;
import co.caudal.application.auth.LogoutCommandHandler;
import co.caudal.application.auth.PasswordRenewal;
import co.caudal.application.auth.RefreshSessionUseCase;
import co.caudal.application.auth.RefreshTokenService;
import co.caudal.application.auth.ResetPasswordCommandHandler;
import co.caudal.application.auth.ResetPasswordPorts;
import co.caudal.application.auth.SessionInvalidator;
import co.caudal.application.mfa.MfaVerifier;
import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.LoginAttemptPort;
import co.caudal.application.port.out.MembershipAdminPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.MfaFactorPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordHistoryPort;
import co.caudal.application.port.out.PasswordResetGrantPort;
import co.caudal.application.port.out.PermissionPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.RateLimiterPort;
import co.caudal.application.port.out.RefreshTokenPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.TokenVersionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.application.port.out.UserProfilePort;
import co.caudal.application.ratelimit.RateLimit;
import co.caudal.application.user.CreateUserCommandHandler;
import co.caudal.application.user.ResetGrantSettings;
import co.caudal.domain.user.LockoutPolicy;
import co.caudal.domain.user.PasswordPolicy;
import co.caudal.infrastructure.ratelimit.RateLimitProperties;
import co.caudal.infrastructure.security.Argon2Properties;
import co.caudal.infrastructure.security.LoginProperties;
import co.caudal.infrastructure.security.RefreshProperties;
import co.caudal.infrastructure.security.ResetGrantProperties;
import co.caudal.shared.PhysicalConstants;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the use cases and services of the {@code application} layer, which has no Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RefreshProperties.class, ResetGrantProperties.class})
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
      MfaFactorPort mfaFactors,
      MfaVerifier mfa,
      RateLimiterPort rateLimiter,
      UnitOfWorkPort unitOfWork,
      LoginProperties login,
      RefreshProperties refresh,
      RateLimitProperties rates,
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
            refresh::ttlFor,
            new RateLimit(rates.loginPerMinute(), Duration.ofMinutes(1)));
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
            mfaFactors,
            mfa,
            rateLimiter,
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

  @Bean
  LogoutCommandHandler logoutCommandHandler(
      RefreshTokenPort tokens, UnitOfWorkPort unitOfWork, Clock clock) {
    return new LogoutCommandHandler(tokens, unitOfWork, clock);
  }

  @Bean
  SessionInvalidator sessionInvalidator(
      UserAccountPort accounts, RefreshTokenPort tokens, Clock clock) {
    return new SessionInvalidator(accounts, tokens, clock);
  }

  @Bean
  LogoutAllCommandHandler logoutAllCommandHandler(
      SessionInvalidator sessions, UnitOfWorkPort unitOfWork) {
    return new LogoutAllCommandHandler(sessions, unitOfWork);
  }

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  PasswordRenewal passwordRenewal(
      PasswordPolicy policy,
      PasswordHasherPort passwords,
      PasswordHistoryPort history,
      UserAccountPort accounts,
      Argon2Properties argon2,
      Clock clock) {
    return new PasswordRenewal(
        policy, passwords, history, accounts, argon2.passwordHistorySize(), clock);
  }

  @Bean
  ChangePasswordCommandHandler changePasswordCommandHandler(
      UserAccountPort accounts,
      PasswordHasherPort passwords,
      PasswordRenewal renewal,
      SessionInvalidator sessions,
      UnitOfWorkPort unitOfWork) {
    return new ChangePasswordCommandHandler(accounts, passwords, renewal, sessions, unitOfWork);
  }

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  ResetPasswordCommandHandler resetPasswordCommandHandler(
      LoginAccountPort accounts,
      PasswordResetGrantPort grants,
      MembershipPort memberships,
      PasswordHasherPort passwords,
      PasswordRenewal renewal,
      SessionInvalidator sessions,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new ResetPasswordCommandHandler(
        new ResetPasswordPorts(accounts, grants, memberships, passwords),
        renewal,
        sessions,
        unitOfWork,
        clock);
  }

  @Bean
  ResetGrantSettings resetGrantSettings(ResetGrantProperties properties) {
    return new ResetGrantSettings(Duration.ofMinutes(properties.ttlMinutes()));
  }

  // A Spring bean method lists its collaborators; grouping them would only hide the wiring.
  @SuppressWarnings("checkstyle:ParameterNumber")
  @Bean
  CreateUserCommandHandler createUserCommandHandler(
      UserAccountPort accounts,
      MembershipAdminPort memberships,
      PasswordResetGrantPort grants,
      PasswordHasherPort passwords,
      ResetGrantSettings settings,
      UnitOfWorkPort unitOfWork,
      Clock clock) {
    return new CreateUserCommandHandler(
        accounts, memberships, grants, passwords, settings, unitOfWork, clock);
  }
}
