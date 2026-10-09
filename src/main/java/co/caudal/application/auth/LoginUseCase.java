package co.caudal.application.auth;

import co.caudal.application.auth.LoginAttempt.FailureReason;
import co.caudal.application.port.out.AccessTokenIssuerPort;
import co.caudal.application.port.out.LoginAccountPort;
import co.caudal.application.port.out.LoginAttemptPort;
import co.caudal.application.port.out.MembershipPort;
import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PrivacyHasherPort;
import co.caudal.application.port.out.SecurityEventPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.security.SecurityEvent;
import co.caudal.domain.security.SecurityEventType;
import co.caudal.domain.security.Severity;
import co.caudal.domain.user.InvalidCredentialsException;
import co.caudal.domain.user.LockoutPolicy;
import co.caudal.domain.user.LockoutPolicy.LockoutDecision;
import co.caudal.domain.user.Membership;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.domain.user.Username;
import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs a person in (docs/Seguridad.md, section 2.2; .agents/security.md, section 4.1).
 *
 * <p>Every failure is {@code INVALID_CREDENTIALS} with the same body, whether the user does not
 * exist, the password is wrong or the account is locked, disabled or without a membership, and
 * every path that reaches the password step spends one Argon2id verification (a decoy hash when
 * there is no usable account), so neither the message nor the time reveals which one it was.
 * Failures are recorded and committed before the error is raised. The only different answer is
 * {@code RATE_LIMITED} when one IP has too many failed attempts, which is decided before the
 * password is looked at.
 */
public class LoginUseCase {

  private static final String UNKNOWN_IP = "unknown";

  private final LoginAccountPort accounts;
  private final LoginAttemptPort attempts;
  private final MembershipPort memberships;
  private final PasswordHasherPort passwords;
  private final PrivacyHasherPort hasher;
  private final AccessTokenIssuerPort accessTokens;
  private final RefreshTokenService refreshTokens;
  private final SecurityEventPort events;
  private final UnitOfWorkPort unitOfWork;
  private final LockoutPolicy lockout;
  private final LoginSettings settings;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param ports the ports it needs, grouped to keep the constructor small
   * @param lockout lock rules
   * @param settings windows and lifetimes
   * @param clock source of time
   */
  public LoginUseCase(
      LoginPorts ports, LockoutPolicy lockout, LoginSettings settings, Clock clock) {
    this.accounts = ports.accounts();
    this.attempts = ports.attempts();
    this.memberships = ports.memberships();
    this.passwords = ports.passwords();
    this.hasher = ports.hasher();
    this.accessTokens = ports.accessTokens();
    this.refreshTokens = ports.refreshTokens();
    this.events = ports.events();
    this.unitOfWork = ports.unitOfWork();
    this.lockout = lockout;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Signs in.
   *
   * @param input username, password and caller data
   * @return the tokens and the person
   * @throws InvalidCredentialsException for any failure of the credentials or the account
   * @throws DomainException with {@code RATE_LIMITED} if the IP has too many failures
   */
  public LoginResult execute(LoginInput input) {
    String ipHmac = hasher.hmac(input.ipAddress() == null ? UNKNOWN_IP : input.ipAddress());
    ClientContext client = new ClientContext(ipHmac, input.userAgent());
    NormalizedPassword password = NormalizedPassword.of(input.password());
    Optional<Username> username = Username.tryParse(input.username());
    String usernameHmac = hasher.hmac(boundedUsername(input.username()));

    Step step = unitOfWork.execute(() -> authenticate(username, password, usernameHmac, ipHmac));
    if (step.failure() != null) {
      throw step.failure();
    }
    LoginAccount account = step.account();
    Step done =
        unitOfWork.executeAs(
            SessionScope.ofUser(account.id()),
            () -> complete(account, password, usernameHmac, client));
    if (done.failure() != null) {
      throw done.failure();
    }
    return done.result();
  }

  private Step authenticate(
      Optional<Username> username,
      NormalizedPassword password,
      String usernameHmac,
      String ipHmac) {
    Instant now = clock.instant();
    if (attempts.countFailuresByIp(ipHmac, now.minus(settings.ipWindow()))
        >= settings.ipMaxAttempts()) {
      attempts.record(
          new LoginAttempt(null, usernameHmac, ipHmac, false, FailureReason.RATE_LIMITED));
      events.record(
          new SecurityEvent(
              SecurityEventType.RATE_LIMITED,
              Severity.LOW,
              null,
              null,
              ipHmac,
              Map.of("scope", "login_ip")));
      return Step.fail(new DomainException(ErrorCode.RATE_LIMITED));
    }
    Optional<LoginAccount> found = username.flatMap(name -> accounts.findByUsername(name.value()));
    if (found.isEmpty()) {
      return rejectWithoutAccount(
          password, usernameHmac, ipHmac, FailureReason.INVALID_CREDENTIALS, null);
    }
    LoginAccount account = found.get();
    if (LoginAccount.DISABLED.equals(account.status())) {
      return rejectWithoutAccount(
          password, usernameHmac, ipHmac, FailureReason.DISABLED, account.id());
    }
    if (isLocked(account, now)) {
      return rejectWithoutAccount(
          password, usernameHmac, ipHmac, FailureReason.LOCKED, account.id());
    }
    if (!passwords.matches(password, account.passwordHash())) {
      return registerFailure(account, usernameHmac, ipHmac, now);
    }
    return Step.authenticated(account);
  }

  private Step rejectWithoutAccount(
      NormalizedPassword password,
      String usernameHmac,
      String ipHmac,
      FailureReason reason,
      UUID userId) {
    passwords.verifyAgainstDecoy(password);
    attempts.record(new LoginAttempt(userId, usernameHmac, ipHmac, false, reason));
    return Step.fail(new InvalidCredentialsException());
  }

  private Step registerFailure(
      LoginAccount account, String usernameHmac, String ipHmac, Instant now) {
    attempts.record(
        new LoginAttempt(
            account.id(), usernameHmac, ipHmac, false, FailureReason.INVALID_CREDENTIALS));
    int failures = attempts.countConsecutiveFailures(account.id(), now.minus(settings.window()));
    LockoutDecision decision = lockout.evaluate(failures, account.lockoutLevel(), now);
    accounts.saveFailure(
        account.id(), decision.locked(), failures, decision.lockedUntil(), decision.level());
    if (decision.locked()) {
      events.record(
          new SecurityEvent(
              SecurityEventType.ACCOUNT_LOCKED,
              Severity.MEDIUM,
              account.id(),
              null,
              ipHmac,
              Map.of("level", decision.level())));
    }
    return Step.fail(new InvalidCredentialsException());
  }

  private Step complete(
      LoginAccount account,
      NormalizedPassword password,
      String usernameHmac,
      ClientContext client) {
    Instant now = clock.instant();
    List<Membership> active = memberships.findActive(account.id(), now);
    if (active.isEmpty()) {
      attempts.record(
          new LoginAttempt(
              account.id(), usernameHmac, client.ipHmac(), false, FailureReason.DISABLED));
      return Step.fail(new InvalidCredentialsException());
    }
    Membership membership = active.getFirst();
    if (passwords.needsRehash(account.passwordHash())) {
      accounts.updatePasswordHash(account.id(), passwords.hash(password));
    }
    IssuedRefreshToken refresh =
        refreshTokens.issueNewFamily(
            account.id(), settings.refreshTtl().apply(membership.role()), client);
    IssuedAccessToken access =
        accessTokens.issue(
            new AccessTokenSubject(
                account.id(), membership.role(), membership.aqueductId(), account.tokenVersion()));
    accounts.saveSuccess(account.id(), now, client.ipHmac());
    attempts.record(new LoginAttempt(account.id(), usernameHmac, client.ipHmac(), true, null));
    return Step.done(
        new LoginResult(
            access,
            refresh,
            new LoginResult.SessionUser(
                account.id(),
                account.username(),
                account.fullName(),
                membership.role(),
                membership.aqueductId(),
                account.mustChangePassword())));
  }

  private static boolean isLocked(LoginAccount account, Instant now) {
    return LoginAccount.LOCKED.equals(account.status())
        && account.lockedUntil() != null
        && account.lockedUntil().isAfter(now);
  }

  private static String boundedUsername(String typed) {
    String value = typed == null ? "" : typed.strip();
    return value.length() > FieldLimits.LOGIN_USERNAME_INPUT_MAX
        ? value.substring(0, FieldLimits.LOGIN_USERNAME_INPUT_MAX)
        : value;
  }

  /** Outcome of one transaction: a failure to raise after the commit, or what to continue with. */
  private record Step(DomainException failure, LoginAccount account, LoginResult result) {

    static Step fail(DomainException failure) {
      return new Step(failure, null, null);
    }

    static Step authenticated(LoginAccount account) {
      return new Step(null, account, null);
    }

    static Step done(LoginResult result) {
      return new Step(null, null, result);
    }
  }
}
