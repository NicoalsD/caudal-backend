package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.List;
import java.util.UUID;

/** Reads the tanks, sectors and valves of the aqueduct of the session. */
public class ListNetworkUseCase {

  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the use case.
   *
   * @param network network
   * @param unitOfWork transaction boundary
   */
  public ListNetworkUseCase(NetworkPort network, UnitOfWorkPort unitOfWork) {
    this.network = network;
    this.unitOfWork = unitOfWork;
  }

  /**
   * Lists the tanks.
   *
   * @param caller who asks
   * @return the tanks
   */
  public List<TankView> tanks(Caller caller) {
    return unitOfWork.executeAs(caller.scope(), network::tanks);
  }

  /**
   * Lists the sectors.
   *
   * @param caller who asks
   * @return the sectors
   */
  public List<SectorView> sectors(Caller caller) {
    return unitOfWork.executeAs(caller.scope(), network::sectors);
  }

  /**
   * Lists the valves of a sector.
   *
   * @param caller who asks
   * @param sectorId the sector
   * @return the valves
   * @throws DomainException with {@code NOT_FOUND} if the sector is not in the aqueduct
   */
  public List<ValveView> valves(Caller caller, UUID sectorId) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          network.sector(sectorId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
          return network.valves(sectorId);
        });
  }
}
