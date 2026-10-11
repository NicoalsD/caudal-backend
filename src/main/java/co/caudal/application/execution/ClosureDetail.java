package co.caudal.application.execution;

import java.util.List;

/**
 * A closed day with the executions of its shifts.
 *
 * @param closure the closure
 * @param executions the executions, oldest first
 */
public record ClosureDetail(ClosureView closure, List<ExecutionView> executions) {

  /** Copies the executions. */
  public ClosureDetail {
    executions = List.copyOf(executions);
  }
}
