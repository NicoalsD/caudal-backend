package co.caudal.domain.autopilot;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Map;

/** What a recommendation of the autopilot proposes. */
public enum AutopilotAction {
  /** Open a valve. */
  OPEN(true),
  /** Close a valve. */
  CLOSE(true),
  /** Do nothing for now. */
  WAIT(false),
  /** Send someone to look. */
  INSPECT(false);

  private final boolean movesValve;

  AutopilotAction(boolean movesValve) {
    this.movesValve = movesValve;
  }

  /**
   * Restores the action from its code.
   *
   * @param code the code
   * @return the action
   * @throws DomainException with {@code VALIDATION_ERROR} if the code is unknown
   */
  public static AutopilotAction of(String code) {
    if (code != null) {
      for (AutopilotAction action : values()) {
        if (action.name().equals(code)) {
          return action;
        }
      }
    }
    throw new DomainException(ErrorCode.VALIDATION_ERROR, Map.of("field", "action"));
  }

  /**
   * Whether the action moves a valve, so it needs a valve and can become a command.
   *
   * @return true for OPEN and CLOSE
   */
  public boolean movesValve() {
    return movesValve;
  }
}
