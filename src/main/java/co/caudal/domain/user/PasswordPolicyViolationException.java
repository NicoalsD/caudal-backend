package co.caudal.domain.user;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;
import java.util.Map;

/** A new password does not meet the policy. Details carry the broken rule, never the password. */
public class PasswordPolicyViolationException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** The rule that the password broke. */
  public enum Rule {
    TOO_SHORT,
    TOO_LONG,
    COMMON,
    CONTAINS_USERNAME
  }

  private final Rule rule;

  /**
   * Creates the exception.
   *
   * @param rule the broken rule
   */
  public PasswordPolicyViolationException(Rule rule) {
    super(ErrorCode.PASSWORD_POLICY_VIOLATION, Map.of("rule", rule.name()));
    this.rule = rule;
  }

  public Rule rule() {
    return rule;
  }
}
