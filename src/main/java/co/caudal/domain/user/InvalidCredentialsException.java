package co.caudal.domain.user;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;

/**
 * Login failed. The same exception, code and message are used whether the user does not exist, the
 * password is wrong, or the account is locked, disabled or without access, so the answer never
 * reveals which one it was.
 */
public class InvalidCredentialsException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception, answered as {@code 401 INVALID_CREDENTIALS}. */
  public InvalidCredentialsException() {
    super(ErrorCode.INVALID_CREDENTIALS);
  }
}
