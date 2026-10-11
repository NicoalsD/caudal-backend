package co.caudal.domain.execution;

import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;

/**
 * Whether a day of service is still open (docs/Reglas-de-negocio.md, section 14). An open day takes
 * executions and closes once; a closed day takes nothing more, and closing it again is a conflict.
 *
 * @pattern P18 State
 */
public sealed interface DayState permits DayState.Open, DayState.Closed {

  /**
   * The state of a day.
   *
   * @param closed true if it has a closure
   * @return the state
   */
  static DayState of(boolean closed) {
    return closed ? new Closed() : new Open();
  }

  /**
   * Closes the day.
   *
   * @return the closed day
   * @throws DomainException CONFLICT if it was already closed
   */
  DayState close();

  /**
   * Tells whether the day still takes executions.
   *
   * @return true while open
   */
  boolean takesExecutions();

  /** A day not closed yet. */
  record Open() implements DayState {
    @Override
    public DayState close() {
      return new Closed();
    }

    @Override
    public boolean takesExecutions() {
      return true;
    }
  }

  /** A closed day. */
  record Closed() implements DayState {
    @Override
    public DayState close() {
      throw new DomainException(ErrorCode.CONFLICT);
    }

    @Override
    public boolean takesExecutions() {
      return false;
    }
  }
}
