package co.caudal.api.mapper;

import co.caudal.api.dto.response.SectorResponse;
import co.caudal.api.dto.response.TankResponse;
import co.caudal.api.dto.response.ValveResponse;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.network.ValveView;

/** Maps the network to response DTOs. */
public final class NetworkMapper {

  private NetworkMapper() {}

  /**
   * Maps a tank.
   *
   * @param tank the tank
   * @return the response
   */
  public static TankResponse toResponse(TankView tank) {
    return new TankResponse(
        tank.id(),
        tank.name(),
        tank.gaugeMin(),
        tank.gaugeMax(),
        tank.gaugeStep(),
        tank.capacityLiters(),
        tank.active());
  }

  /**
   * Maps a sector.
   *
   * @param sector the sector
   * @return the response
   */
  public static SectorResponse toResponse(SectorView sector) {
    return new SectorResponse(
        sector.id(),
        sector.code(),
        sector.name(),
        sector.parentSectorId(),
        sector.householdsCount(),
        sector.active());
  }

  /**
   * Maps a valve.
   *
   * @param valve the valve
   * @return the response
   */
  public static ValveResponse toResponse(ValveView valve) {
    return new ValveResponse(
        valve.id(),
        valve.code(),
        valve.name(),
        valve.locationHint(),
        valve.motorized(),
        valve.failSafePosition(),
        valve.active());
  }
}
