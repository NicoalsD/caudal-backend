package co.caudal.application.minutes;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.MinutesPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.UUID;

/** Reads minutes (docs/API.md, section 13.2). */
public class GetMinutesUseCase {

  private final MinutesPort minutes;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param minutes minutes
   * @param unitOfWork transaction boundary
   */
  public GetMinutesUseCase(MinutesPort minutes, UnitOfWorkPort unitOfWork) {
    this.minutes = minutes;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads one.
   *
   * @param caller who asks
   * @param id the minutes
   * @return the minutes
   * @throws DomainException NOT_FOUND if they do not exist in the aqueduct
   */
  public MinutesView execute(Caller caller, UUID id) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> minutes.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
  }
}
