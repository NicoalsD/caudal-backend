package co.caudal.application.execution;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.ClosurePort;
import co.caudal.application.port.out.ExecutionPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.LocalDate;

/** Reads the closure of a day with the executions of its shifts (docs/API.md, section 11.2). */
public class GetDayClosureUseCase {

  private final ClosurePort closures;
  private final ExecutionPort executions;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param closures closures
   * @param executions executions
   * @param unitOfWork transaction boundary
   */
  public GetDayClosureUseCase(
      ClosurePort closures, ExecutionPort executions, UnitOfWorkPort unitOfWork) {
    this.closures = closures;
    this.executions = executions;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a closed day.
   *
   * @param caller who asks
   * @param serviceDate the day
   * @return the closure and its executions
   * @throws DomainException NOT_FOUND if the day is not closed
   */
  public ClosureDetail execute(Caller caller, LocalDate serviceDate) {
    return unitOfWork.executeAs(
        caller.scope(),
        () ->
            new ClosureDetail(
                closures
                    .find(serviceDate)
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)),
                executions.forDay(serviceDate)));
  }
}
