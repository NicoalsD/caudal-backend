package co.caudal.domain.session;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;

/** The session was closed or revoked; the person must sign in again ({@code SESSION_REVOKED}). */
public class SessionRevokedException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the exception, answered as {@code 401 SESSION_REVOKED}. */
  public SessionRevokedException() {
    super(ErrorCode.SESSION_REVOKED);
  }
}
