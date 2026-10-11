package co.caudal.application.reading;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.ReadingPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.UUID;

/** Reads one reading with its warnings and corrections (docs/API.md, section 7.4). */
public class GetReadingUseCase {

  private final ReadingPort readings;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param readings readings
   * @param unitOfWork transaction boundary
   */
  public GetReadingUseCase(ReadingPort readings, UnitOfWorkPort unitOfWork) {
    this.readings = readings;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads a reading.
   *
   * @param caller who asks
   * @param id the reading
   * @return the reading with its history
   * @throws DomainException with {@code NOT_FOUND} if it is not in the aqueduct
   */
  public ReadingDetail execute(Caller caller, UUID id) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          ReadingView reading =
              readings.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return new ReadingDetail(
              reading,
              readings.effectiveValue(id).orElse(reading.gaugeValue()),
              readings.issues(id),
              readings.corrections(id));
        });
  }
}
