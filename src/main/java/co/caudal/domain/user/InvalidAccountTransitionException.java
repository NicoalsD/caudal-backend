package co.caudal.domain.user;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.io.Serial;
import java.util.Map;

/** An administrator asked for a change of account status that the current status does not allow. */
public class InvalidAccountTransitionException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param from the current status
   * @param to the wanted status
   */
  public InvalidAccountTransitionException(String from, String to) {
    super(ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", from, "to", to));
  }
}
