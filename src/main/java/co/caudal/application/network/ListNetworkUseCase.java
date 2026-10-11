package co.caudal.application.network;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.network.NetworkNode;
import co.caudal.domain.network.NetworkTree;
import co.caudal.domain.network.NetworkTree.SectorRow;
import co.caudal.domain.network.ValveNode;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Builds the tree of the network: top sectors with their valves and sub-sectors.
   *
   * @param caller who asks
   * @return the top sectors
   */
  public List<NetworkNodeView> tree(Caller caller) {
    return unitOfWork.executeAs(
        caller.scope(),
        () -> {
          List<SectorView> sectors = network.sectors();
          Map<UUID, List<ValveNode>> valves = new LinkedHashMap<>();
          for (SectorView sector : sectors) {
            valves.put(
                sector.id(),
                network.valves(sector.id()).stream()
                    .map(v -> new ValveNode(v.id(), v.code(), v.name(), v.active(), v.motorized()))
                    .toList());
          }
          return NetworkTree.build(
                  sectors.stream()
                      .map(
                          s ->
                              new SectorRow(
                                  s.id(),
                                  s.code(),
                                  s.name(),
                                  s.parentSectorId(),
                                  s.householdsCount(),
                                  s.active()))
                      .toList(),
                  valves)
              .stream()
              .map(ListNetworkUseCase::view)
              .toList();
        });
  }

  private static NetworkNodeView view(NetworkNode node) {
    return new NetworkNodeView(
        node instanceof ValveNode ? "VALVE" : "SECTOR",
        node.id(),
        node.code(),
        node.name(),
        node.active(),
        node.households(),
        node.activeValves().size(),
        node.children().stream().map(ListNetworkUseCase::view).toList());
  }
}
