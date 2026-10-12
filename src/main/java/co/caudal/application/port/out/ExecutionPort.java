package co.caudal.application.port.out;

import co.caudal.application.execution.ExecutionView;
import co.caudal.application.execution.NewExecution;
import co.caudal.application.execution.ShiftContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executions of shifts. Row level security limits the aqueduct. */
public interface ExecutionPort {

  /**
   * A shift and its proposal.
   *
   * @param itemId the shift
   * @return the shift, or empty
   */
  Optional<ShiftContext> shift(UUID itemId);

  /**
   * An execution.
   *
   * @param id the execution
   * @return the execution, or empty
   */
  Optional<ExecutionView> find(UUID id);

  /**
   * Tells whether another execution already replaces this one.
   *
   * @param id the execution
   * @return true if replaced
   */
  boolean superseded(UUID id);

  /**
   * Tells whether a shift has an execution not replaced by another.
   *
   * @param itemId the shift
   * @return true if it has one
   */
  boolean hasCurrent(UUID itemId);

  /**
   * Stores an execution.
   *
   * @param aqueductId the aqueduct
   * @param execution the execution
   * @return the stored execution
   */
  ExecutionView insert(UUID aqueductId, NewExecution execution);

  /**
   * The executions of the shifts of a day, oldest first.
   *
   * @param serviceDate the day
   * @return the executions
   */
  List<ExecutionView> forDay(LocalDate serviceDate);
}
