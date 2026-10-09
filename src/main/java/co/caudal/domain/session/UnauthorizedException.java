package co.caudal.domain.session;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;

/** There is no valid session: the credential is missing, unknown or expired. No detail is given. */
public class UnauthorizedException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception, answered as {@code 401 UNAUTHORIZED}. */
  public UnauthorizedException() {
    super(ErrorCode.UNAUTHORIZED);
  }
}
