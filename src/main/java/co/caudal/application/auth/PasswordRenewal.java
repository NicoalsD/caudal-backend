package co.caudal.application.auth;

import co.caudal.application.port.out.PasswordHasherPort;
import co.caudal.application.port.out.PasswordHistoryPort;
import co.caudal.application.port.out.UserAccountPort;
import co.caudal.domain.user.NormalizedPassword;
import co.caudal.domain.user.PasswordPolicy;
import co.caudal.domain.user.PasswordPolicyViolationException;
import co.caudal.domain.user.PasswordPolicyViolationException.Rule;
import co.caudal.domain.user.Username;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets a new password for an account, with every rule that a new password has to meet
 * (docs/Seguridad.md, section 2.1): the length, common password and username rules of {@link
 * PasswordPolicy}, and not being one of the last {@code historySize} passwords (the current one and
 * the previous ones in {@code iam.password_history}).
 *
 * <p>The old hash goes to the history in the same transaction that stores the new one. It does not
 * revoke sessions: the caller does that with {@link SessionInvalidator}, because each use case has
 * its own reason.
 */
public class PasswordRenewal {

  private final PasswordPolicy policy;
  private final PasswordHasherPort passwords;
  private final PasswordHistoryPort history;
  private final UserAccountPort accounts;
  private final int historySize;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param policy rules for a new password
   * @param passwords hashing of passwords
   * @param history previous hashes
   * @param accounts accounts
   * @param historySize how many of the last passwords cannot be used again (the current one counts)
   * @param clock source of time
   */
  public PasswordRenewal(
      PasswordPolicy policy,
      PasswordHasherPort passwords,
      PasswordHistoryPort history,
      UserAccountPort accounts,
      int historySize,
      Clock clock) {
    this.policy = policy;
    this.passwords = passwords;
    this.history = history;
    this.accounts = accounts;
    this.historySize = historySize;
    this.clock = clock;
  }

  /**
   * Checks the new password and stores it. Must run in a transaction.
   *
   * @param account the account, with its current hash
   * @param newPassword the password the person chose
   * @param mustChangePassword the value of the flag after the change
   * @throws PasswordPolicyViolationException if the password breaks a rule
   */
  public void renew(
      LoginAccount account, NormalizedPassword newPassword, boolean mustChangePassword) {
    policy.check(newPassword, Username.parse(account.username()));
    List<String> remembered = new ArrayList<>();
    remembered.add(account.passwordHash());
    remembered.addAll(history.findRecent(account.id(), Math.max(historySize - 1, 0)));
    for (String hash : remembered) {
      if (passwords.matches(newPassword, hash)) {
        throw new PasswordPolicyViolationException(Rule.RECENTLY_USED);
      }
    }
    history.add(account.id(), account.passwordHash());
    accounts.replacePassword(
        account.id(), passwords.hash(newPassword), clock.instant(), mustChangePassword);
  }
}
