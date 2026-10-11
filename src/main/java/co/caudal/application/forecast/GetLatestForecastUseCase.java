package co.caudal.application.forecast;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.ForecastRunPort;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.UUID;

/** Reads the latest forecast shown for a tank (docs/API.md, section 8.2); never the shadow. */
public class GetLatestForecastUseCase {

  private final NetworkPort network;
  private final ForecastRunPort runs;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param network tanks
   * @param runs stored runs
   * @param unitOfWork transaction boundary
   */
  public GetLatestForecastUseCase(
      NetworkPort network, ForecastRunPort runs, UnitOfWorkPort unitOfWork) {
    this.network = network;
    this.runs = runs;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Reads the latest forecast.
   *
   * @param caller who asks
   * @param tankId the tank
   * @return the run
   * @throws DomainException with {@code NOT_FOUND} for an unknown tank or a tank never forecast
   */
  public StoredForecast execute(Caller caller, UUID tankId) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          network.tank(tankId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return runs.latestPrimary(tankId)
              .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        });
  }
}
