package co.caudal.domain.user;

import co.caudal.domain.user.PasswordPolicyViolationException.Rule;
import co.caudal.shared.FieldLimits;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Policy for a new password (docs/Seguridad.md, section 2.1): 12 to 128 code points after NFKC, not
 * in the local list of common passwords, and without the username inside. There are no composition
 * rules (upper case, digits, symbols).
 *
 * <p>It is only applied when a password is created or changed, never on login, because older
 * accounts may predate the current policy. The history of the last passwords needs the stored
 * hashes and is checked by the use case that changes the password.
 */
public final class PasswordPolicy {

  private final Set<String> commonPasswords;

  /**
   * Creates the policy.
   *
   * @param commonPasswords the common passwords; they are compared in NFKC and lower case
   */
  public PasswordPolicy(Set<String> commonPasswords) {
    Objects.requireNonNull(commonPasswords, "commonPasswords");
    this.commonPasswords =
        commonPasswords.stream()
            .map(PasswordPolicy::canonical)
            .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Checks a new password.
   *
   * @param password the normalized password
   * @param username the username of the account that will use it
   * @throws PasswordPolicyViolationException with the first rule that is broken
   */
  public void check(NormalizedPassword password, Username username) {
    int length = password.length();
    if (length < FieldLimits.PASSWORD_MIN) {
      throw new PasswordPolicyViolationException(Rule.TOO_SHORT);
    }
    if (length > FieldLimits.PASSWORD_MAX) {
      throw new PasswordPolicyViolationException(Rule.TOO_LONG);
    }
    String lowered = lower(password.value());
    if (commonPasswords.contains(lowered)) {
      throw new PasswordPolicyViolationException(Rule.COMMON);
    }
    if (lowered.contains(username.value())) {
      throw new PasswordPolicyViolationException(Rule.CONTAINS_USERNAME);
    }
  }

  private static String canonical(String entry) {
    return lower(NormalizedPassword.of(entry.strip()).value());
  }

  private static String lower(String text) {
    return text.toLowerCase(Locale.ROOT);
  }
}
